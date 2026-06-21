package com.police.vision.video.service;

import com.police.vision.video.config.VideoConfig;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.mapper.CameraDeviceMapper;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoStreamService {

    private final CameraDeviceMapper cameraDeviceMapper;
    private final VideoConfig videoConfig;

    private final Map<String, StreamSession> activeStreams = new ConcurrentHashMap<>();
    private static final int STREAM_TIMEOUT = 300000;

    public void startHttpFlvStream(String cameraId, HttpServletResponse response) throws Exception {
        CameraDevice camera = cameraDeviceMapper.selectByDeviceId(cameraId);
        if (camera == null) {
            throw new RuntimeException("摄像头不存在：" + cameraId);
        }

        String streamKey = "http_" + cameraId + "_" + Thread.currentThread().getId();
        log.info("开始HTTP-FLV直播流：cameraId={}, streamKey={}", cameraId, streamKey);

        response.setContentType("video/x-flv");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");

        StreamSession session = new StreamSession();
        activeStreams.put(streamKey, session);

        try (FFmpegFrameGrabber grabber = createGrabber(camera.getRtspUrl());
             OutputStream out = response.getOutputStream();
             PipedInputStream pin = new PipedInputStream(1024 * 1024);
             PipedOutputStream pout = new PipedOutputStream(pin)) {

            grabber.start();

            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            double frameRate = grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 25;
            int audioChannels = grabber.getAudioChannels();
            int sampleRate = grabber.getSampleRate() > 0 ? grabber.getSampleRate() : 44100;

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(pout, width, height, audioChannels)) {
                configureFlvRecorder(recorder, frameRate, width, height, audioChannels, sampleRate);
                recorder.start();

                writeFlvHeader(out);

                Thread copyThread = new Thread(() -> {
                    byte[] buffer = new byte[4096];
                    int len;
                    try {
                        while (!session.stopped.get() && (len = pin.read(buffer)) != -1) {
                            if (len > 0) {
                                out.write(buffer, 0, len);
                                out.flush();
                            }
                        }
                    } catch (IOException e) {
                        log.debug("FLV流写入中断：{}", e.getMessage());
                    }
                }, "FLV-Copy-" + streamKey);
                copyThread.setDaemon(true);
                copyThread.start();

                long lastWriteTime = System.currentTimeMillis();
                long frameCount = 0;
                long startTime = System.currentTimeMillis();

                while (!session.stopped.get()) {
                    if (System.currentTimeMillis() - lastWriteTime > STREAM_TIMEOUT) {
                        log.warn("FLV流超时断开：streamKey={}", streamKey);
                        break;
                    }

                    Frame frame = grabber.grab();
                    if (frame == null) {
                        Thread.sleep(10);
                        continue;
                    }

                    recorder.record(frame);
                    frameCount++;
                    lastWriteTime = System.currentTimeMillis();

                    if (frameCount % 500 == 0) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        double fps = frameCount * 1000.0 / elapsed;
                        log.debug("FLV流状态：streamKey={}, frames={}, avgFps={:.2f}",
                                streamKey, frameCount, fps);
                    }
                }

                session.stopped.set(true);
                recorder.stop();
                grabber.stop();

                log.info("FLV流结束：streamKey={}, totalFrames={}", streamKey, frameCount);
            }
        } catch (Exception e) {
            log.error("FLV直播流异常：{}", e.getMessage());
            throw e;
        } finally {
            activeStreams.remove(streamKey);
        }
    }

    public byte[] getSnapshot(String cameraId) throws Exception {
        CameraDevice camera = cameraDeviceMapper.selectByDeviceId(cameraId);
        if (camera == null) {
            throw new RuntimeException("摄像头不存在：" + cameraId);
        }

        try (FFmpegFrameGrabber grabber = createGrabber(camera.getRtspUrl())) {
            grabber.start();

            Frame frame = null;
            for (int i = 0; i < 30; i++) {
                frame = grabber.grabImage();
                if (frame != null && frame.image != null) {
                    break;
                }
            }
            grabber.stop();

            if (frame == null || frame.image == null) {
                throw new RuntimeException("无法获取摄像头画面");
            }

            return frameToJpeg(frame, grabber.getImageWidth(), grabber.getImageHeight());
        }
    }

    public Map<String, Object> getStreamInfo(String cameraId) {
        CameraDevice camera = cameraDeviceMapper.selectByDeviceId(cameraId);
        if (camera == null) {
            throw new RuntimeException("摄像头不存在：" + cameraId);
        }

        Map<String, Object> info = new java.util.HashMap<>();
        info.put("cameraId", cameraId);
        info.put("cameraName", camera.getDeviceName());
        info.put("status", camera.getStatus());
        info.put("rtspUrl", maskCredentials(camera.getRtspUrl()));
        info.put("flvUrl", "/api/video/stream/flv/" + cameraId);
        info.put("hlsUrl", "/api/video/stream/hls/" + cameraId + "/playlist.m3u8");
        info.put("snapshotUrl", "/api/video/stream/snapshot/" + cameraId);
        info.put("resolution", camera.getResolution());
        info.put("hasPTZ", camera.getPtz() == 1);
        info.put("hasAI", camera.getAiEnabled() == 1);

        int viewerCount = (int) activeStreams.keySet().stream()
                .filter(k -> k.contains("_" + cameraId + "_"))
                .count();
        info.put("viewerCount", viewerCount);

        return info;
    }

    public void stopAllStreams() {
        activeStreams.forEach((key, session) -> session.stopped.set(true));
        activeStreams.clear();
        log.info("已停止所有视频流");
    }

    private FFmpegFrameGrabber createGrabber(String rtspUrl) {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(rtspUrl);
        grabber.setOption("rtsp_transport", videoConfig.getRtsp().getTransport());
        grabber.setOption("stimeout", String.valueOf(videoConfig.getRtsp().getTimeout() * 1000));
        grabber.setOption("buffer_size", String.valueOf(videoConfig.getRtsp().getBufferSize()));
        grabber.setOption("max_delay", "500000");
        grabber.setOption("fflags", "nobuffer");
        grabber.setOption("flags", "low_delay");
        grabber.setOption("analyzeduration", "2000000");
        grabber.setOption("probesize", "2000000");
        return grabber;
    }

    private void configureFlvRecorder(FFmpegFrameRecorder recorder, double frameRate,
                                      int width, int height, int audioChannels, int sampleRate) {
        recorder.setFormat("flv");
        recorder.setFrameRate(frameRate);
        recorder.setVideoCodec(27);
        recorder.setVideoBitrate(1500000);
        recorder.setVideoQuality(23);
        recorder.setGopSize((int) frameRate * 2);
        recorder.setPixelFormat(3);

        if (audioChannels > 0) {
            recorder.setAudioChannels(audioChannels);
            recorder.setSampleRate(sampleRate);
            recorder.setAudioCodec(86018);
            recorder.setAudioBitrate(128000);
        }
    }

    private byte[] frameToJpeg(Frame frame, int width, int height) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(baos, width, height)) {
            recorder.setFormat("image2");
            recorder.setVideoCodec(13);
            recorder.setVideoQuality(1.0);
            recorder.setPixelFormat(0);
            recorder.start();
            recorder.record(frame);
            recorder.stop();
            return baos.toByteArray();
        }
    }

    private void writeFlvHeader(OutputStream out) throws IOException {
        byte[] header = {
                0x46, 0x4C, 0x56,
                0x01,
                0x05,
                0x00, 0x00, 0x00, 0x09,
                0x00, 0x00, 0x00, 0x00
        };
        out.write(header);
        out.flush();
    }

    private String maskCredentials(String url) {
        if (url == null) return null;
        return url.replaceAll("//([^:@]+):([^@]+)@", "//***:***@");
    }

    @PreDestroy
    public void destroy() {
        stopAllStreams();
    }

    private static class StreamSession {
        AtomicBoolean stopped = new AtomicBoolean(false);
        long createTime = System.currentTimeMillis();
    }
}
