package com.graphnexus.infrastructure.storage;

import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.storage.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 文件存储服务实现 — {@link FileStorageService} 的 MinIO 后端。
 *
 * <p>L3 基础设施层薄封装，提供文件上传/下载/删除的基础操作。
 * L2 业务层通过 {@link FileStorageService} 接口操作，不直接注入 {@link MinioClient}。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public void uploadFile(InputStream inputStream, String objectKey, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .stream(inputStream, inputStream.available(), -1)
                            .contentType(contentType)
                            .build()
            );
            log.debug("文件已上传至 MinIO: bucket={}, object={}", minioProperties.getBucket(), objectKey);
        } catch (Exception e) {
            log.error("MinIO 文件上传失败: object={}", objectKey, e);
            throw new BusinessException(ErrorCode.B0001, "文件上传失败", "文件存储服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public InputStream getFile(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("MinIO 文件下载失败: object={}", objectKey, e);
            throw new BusinessException(ErrorCode.A0006, "文件不存在或已被删除", "无法获取该文档的文件内容");
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .build()
            );
            log.debug("文件已从 MinIO 删除: bucket={}, object={}", minioProperties.getBucket(), objectKey);
        } catch (Exception e) {
            log.error("MinIO 文件删除失败: object={}", objectKey, e);
            throw new BusinessException(ErrorCode.B0001, "文件删除失败", "文件存储服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public String getFileUrl(String objectKey) {
        return minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/" + objectKey;
    }

    @Override
    public String extractObjectKey(String filePath) {
        String prefix = minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/";
        if (filePath != null && filePath.startsWith(prefix)) {
            return filePath.substring(prefix.length());
        }
        return filePath;
    }
}
