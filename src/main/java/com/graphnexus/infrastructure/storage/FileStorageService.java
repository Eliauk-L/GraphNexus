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
 * MinIO 文件存储服务。
 *
 * <p>L3 基础设施层薄封装，提供文件上传/下载/删除的基础操作。
 * L2 业务层通过本服务操作 MinIO，不直接注入 {@link MinioClient}。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /**
     * 上传文件到 MinIO。
     *
     * @param inputStream 文件输入流
     * @param objectKey   MinIO 对象键（路径）
     * @param contentType 文件 MIME 类型
     */
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

    /**
     * 从 MinIO 下载文件。
     *
     * @param objectKey MinIO 对象键
     * @return 文件输入流（调用方负责关闭）
     */
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

    /**
     * 从 MinIO 删除文件。
     *
     * @param objectKey MinIO 对象键
     */
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

    /**
     * 根据对象键构建完整文件访问 URL。
     *
     * @param objectKey MinIO 对象键（如 textbooks/uuid.pdf）
     * @return 完整文件访问路径（如 http://localhost:9000/bucket/textbooks/uuid.pdf）
     */
    public String getFileUrl(String objectKey) {
        return minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/" + objectKey;
    }

    /**
     * 从完整文件访问 URL 中提取 MinIO 对象键。
     *
     * @param filePath 完整文件路径（如 http://localhost:9000/bucket/textbooks/uuid.pdf）
     * @return MinIO 对象键（如 textbooks/uuid.pdf），无法提取时返回原值
     */
    public String extractObjectKey(String filePath) {
        String prefix = minioProperties.getEndpoint() + "/" + minioProperties.getBucket() + "/";
        if (filePath != null && filePath.startsWith(prefix)) {
            return filePath.substring(prefix.length());
        }
        return filePath;
    }
}