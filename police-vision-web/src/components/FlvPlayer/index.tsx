import React, { useEffect, useRef, useState } from 'react';
import { Empty, Spin, Button, Tooltip } from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  FullscreenOutlined,
  VideoCameraOutlined
} from '@ant-design/icons';
import flvjs from 'flv.js';

interface FlvPlayerProps {
  url: string;
  onReady?: () => void;
  onError?: (error: any) => void;
}

const FlvPlayer: React.FC<FlvPlayerProps> = ({ url, onReady, onError }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const flvPlayerRef = useRef<flvjs.Player | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    if (!url || !videoRef.current) return;

    setIsLoading(true);
    setHasError(false);

    if (flvjs.isSupported()) {
      try {
        const flvPlayer = flvjs.createPlayer(
          {
            type: 'flv',
            url: url,
            isLive: true
          },
          {
            enableStashBuffer: false,
            stashInitialSize: 128,
            autoCleanupSourceBuffer: true,
            liveBufferLatencyChasing: true,
            liveBufferLatencyMaxLatency: 1.0,
            liveBufferLatencyMinRemain: 0.3
          }
        );

        flvPlayer.attachMediaElement(videoRef.current);
        flvPlayer.load();

        flvPlayer.on(flvjs.Events.LOADING_COMPLETE, () => {
          setIsLoading(false);
          onReady?.();
        });

        flvPlayer.on(flvjs.Events.PLAY, () => {
          setIsPlaying(true);
          setIsLoading(false);
        });

        flvPlayer.on(flvjs.Events.PAUSE, () => {
          setIsPlaying(false);
        });

        flvPlayer.on(flvjs.Events.ERROR, (errorType, errorDetail, errorInfo) => {
          console.error('FLV player error:', errorType, errorDetail, errorInfo);
          setHasError(true);
          setIsLoading(false);
          onError?.({ errorType, errorDetail, errorInfo });
        });

        flvPlayerRef.current = flvPlayer;

        flvPlayer.play().catch((err) => {
          console.warn('Auto play failed:', err);
        });
      } catch (error) {
        console.error('Create FLV player failed:', error);
        setHasError(true);
        setIsLoading(false);
      }
    }

    return () => {
      if (flvPlayerRef.current) {
        flvPlayerRef.current.pause();
        flvPlayerRef.current.unload();
        flvPlayerRef.current.detachMediaElement();
        flvPlayerRef.current.destroy();
        flvPlayerRef.current = null;
      }
    };
  }, [url]);

  const handlePlay = () => {
    if (flvPlayerRef.current && !isPlaying) {
      flvPlayerRef.current.play();
    }
  };

  const handlePause = () => {
    if (flvPlayerRef.current && isPlaying) {
      flvPlayerRef.current.pause();
    }
  };

  const handleReload = () => {
    if (flvPlayerRef.current) {
      setIsLoading(true);
      setHasError(false);
      flvPlayerRef.current.unload();
      flvPlayerRef.current.load();
      flvPlayerRef.current.play().catch(() => {});
    }
  };

  const handleFullscreen = () => {
    if (videoRef.current && videoRef.current.requestFullscreen) {
      videoRef.current.requestFullscreen();
    }
  };

  if (hasError) {
    return (
      <div style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'column',
        gap: 12,
        background: '#1a1f35'
      }}>
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<span style={{ color: '#8c9cb8' }}>视频加载失败</span>}
        />
        <Button type="primary" size="small" onClick={handleReload}>
          重新加载
        </Button>
      </div>
    );
  }

  return (
    <div style={{
      position: 'relative',
      width: '100%',
      height: '100%',
      background: '#0a0e1a'
    }}>
      <video
        ref={videoRef}
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'contain',
          background: '#0a0e1a'
        }}
        muted
        controls={false}
        playsInline
      />

      {isLoading && (
        <div style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'rgba(10, 14, 26, 0.8)'
        }}>
          <Spin indicator={<VideoCameraOutlined spin style={{ fontSize: 32, color: '#1890ff' }} />} />
          <span style={{ color: '#8c9cb8', marginLeft: 8 }}>视频加载中...</span>
        </div>
      )}

      <div style={{
        position: 'absolute',
        bottom: 8,
        left: 8,
        right: 8,
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        background: 'rgba(0, 0, 0, 0.5)',
        padding: '4px 8px',
        borderRadius: 4
      }}>
        <div style={{ display: 'flex', gap: 4 }}>
          {isPlaying ? (
            <Tooltip title="暂停">
              <Button
                type="text"
                size="small"
                icon={<PauseCircleOutlined />}
                style={{ color: '#e6f1ff', padding: '0 4px' }}
                onClick={handlePause}
              />
            </Tooltip>
          ) : (
            <Tooltip title="播放">
              <Button
                type="text"
                size="small"
                icon={<PlayCircleOutlined />}
                style={{ color: '#e6f1ff', padding: '0 4px' }}
                onClick={handlePlay}
              />
            </Tooltip>
          )}
          <Tooltip title="重新加载">
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined />}
              style={{ color: '#e6f1ff', padding: '0 4px' }}
              onClick={handleReload}
            />
          </Tooltip>
        </div>
        <Tooltip title="全屏">
          <Button
            type="text"
            size="small"
            icon={<FullscreenOutlined />}
            style={{ color: '#e6f1ff', padding: '0 4px' }}
            onClick={handleFullscreen}
          />
        </Tooltip>
      </div>
    </div>
  );
};

export default FlvPlayer;
