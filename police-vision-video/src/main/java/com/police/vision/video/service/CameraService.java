package com.police.vision.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.police.vision.common.entity.PageParam;
import com.police.vision.common.entity.PageResult;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import com.police.vision.common.util.SnowflakeIdUtil;
import com.police.vision.video.entity.CameraDevice;
import com.police.vision.video.mapper.CameraDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraDeviceMapper cameraDeviceMapper;

    public CameraDevice getById(Long id) {
        return cameraDeviceMapper.selectById(id);
    }

    public CameraDevice getByDeviceId(String deviceId) {
        return cameraDeviceMapper.selectByDeviceId(deviceId);
    }

    public PageResult<CameraDevice> getPage(PageParam param, String keyword, Integer status, String region) {
        Page<CameraDevice> page = new Page<>(param.getPageNum(), param.getPageSize());
        Page<CameraDevice> result = cameraDeviceMapper.selectCameraPage(page, param, keyword, status, region);
        return PageResult.of(result.getTotal(), result.getRecords(), param.getPageNum(), param.getPageSize());
    }

    public List<CameraDevice> getByStatus(Integer status) {
        return cameraDeviceMapper.selectByStatus(status);
    }

    public List<CameraDevice> getByRegion(String region) {
        return cameraDeviceMapper.selectByRegion(region);
    }

    public List<CameraDevice> getAll() {
        return cameraDeviceMapper.selectList(new LambdaQueryWrapper<CameraDevice>()
                .eq(CameraDevice::getDeleted, 0));
    }

    @Transactional(rollbackFor = Exception.class)
    public void add(CameraDevice camera) {
        CameraDevice exist = cameraDeviceMapper.selectByDeviceId(camera.getDeviceId());
        if (exist != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备ID已存在");
        }
        camera.setId(SnowflakeIdUtil.nextId());
        cameraDeviceMapper.insert(camera);
        log.info("添加摄像头设备成功：deviceId={}", camera.getDeviceId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CameraDevice camera) {
        CameraDevice exist = cameraDeviceMapper.selectById(camera.getId());
        if (exist == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备不存在");
        }
        cameraDeviceMapper.updateById(camera);
        log.info("更新摄像头设备成功：deviceId={}", camera.getDeviceId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        CameraDevice camera = cameraDeviceMapper.selectById(id);
        if (camera == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备不存在");
        }
        camera.setStatus(status);
        cameraDeviceMapper.updateById(camera);
        log.info("更新摄像头状态成功：id={}, status={}", id, status);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CameraDevice camera = cameraDeviceMapper.selectById(id);
        if (camera == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备不存在");
        }
        cameraDeviceMapper.deleteById(id);
        log.info("删除摄像头设备成功：id={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchImport(MultipartFile file) {
        List<CameraDevice> cameras = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 11) {
                    continue;
                }
                CameraDevice camera = new CameraDevice();
                camera.setId(SnowflakeIdUtil.nextId());
                camera.setDeviceId(parts[0].trim());
                camera.setDeviceName(parts[1].trim());
                camera.setBrand(parts[2].trim());
                camera.setModel(parts[3].trim());
                camera.setIpAddress(parts[4].trim());
                camera.setPort(Integer.parseInt(parts[5].trim()));
                camera.setRtspUrl(parts[6].trim());
                camera.setLongitude(new BigDecimal(parts[7].trim()));
                camera.setLatitude(new BigDecimal(parts[8].trim()));
                camera.setRegion(parts[9].trim());
                camera.setInstallLocation(parts[10].trim());
                camera.setStatus(1);
                cameras.add(camera);
            }
            for (CameraDevice camera : cameras) {
                CameraDevice exist = cameraDeviceMapper.selectByDeviceId(camera.getDeviceId());
                if (exist == null) {
                    cameraDeviceMapper.insert(camera);
                }
            }
            log.info("批量导入摄像头设备成功：count={}", cameras.size());
        } catch (Exception e) {
            log.error("批量导入摄像头设备失败：", e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "导入文件格式错误");
        }
    }
}
