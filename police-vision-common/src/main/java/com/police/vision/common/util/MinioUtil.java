package com.police.vision.common.util;

import com.police.vision.common.config.MinioConfig;
import com.police.vision.common.exception.BusinessException;
import com.police.vision.common.result.ResultCode;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinioUtil {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public String uploadFile(MultipartFile file, String bucket) {
        String bucketName = getBucketName(bucket);
        try {
            checkBucketExists(bucketName);
            String fileName = generateFileName(file.getOriginalFilename());
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return fileName;
        } catch (Exception e) {
            log.error("文件上传失败：", e);
            throw new BusinessException(ResultCode.MINIO_ERROR, "文件上传失败");
        }
    }

    public String uploadFile(InputStream inputStream, String fileName, String contentType, String bucket) {
        String bucketName = getBucketName(bucket);
        try {
            checkBucketExists(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .stream(inputStream, -1, 10485760)
                    .contentType(contentType)
                    .build());
            return fileName;
        } catch (Exception e) {
            log.error("文件上传失败：", e);
            throw new BusinessException(ResultCode.MINIO_ERROR, "文件上传失败");
        }
    }

    public InputStream downloadFile(String fileName, String bucket) {
        String bucketName = getBucketName(bucket);
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
        } catch (Exception e) {
            log.error("文件下载失败：", e);
            throw new BusinessException(ResultCode.MINIO_ERROR, "文件下载失败");
        }
    }

    public void deleteFile(String fileName, String bucket) {
        String bucketName = getBucketName(bucket);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
        } catch (Exception e) {
            log.error("文件删除失败：", e);
            throw new BusinessException(ResultCode.MINIO_ERROR, "文件删除失败");
        }
    }

    public String getPresignedUrl(String fileName, String bucket) {
        String bucketName = getBucketName(bucket);
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(fileName)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (Exception e) {
            log.error("获取文件URL失败：", e);
            throw new BusinessException(ResultCode.MINIO_ERROR, "获取文件URL失败");
        }
    }

    public boolean checkFileExists(String fileName, String bucket) {
        String bucketName = getBucketName(bucket);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            log.error("检查Bucket失败：", e);
            throw new BusinessException(ResultCode.MINIO_ERROR, "存储服务异常");
        }
    }

    private String getBucketName(String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            return minioConfig.getBucketName();
        }
        return bucket;
    }

    private String generateFileName(String originalFileName) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String extension = originalFileName != null && originalFileName.contains(".")
                ? originalFileName.substring(originalFileName.lastIndexOf("."))
                : "";
        return dateStr + "/" + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    public String generateVideoPath(String prefix) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
        return prefix + "/" + dateStr + "/" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
    }

    public String generateImagePath(String prefix) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return prefix + "/" + dateStr + "/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
    }
}
