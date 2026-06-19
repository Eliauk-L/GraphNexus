package com.graphnexus.infrastructure.storage;

import java.io.InputStream;

/**
 * 文件存储服务抽象接口 — 提供文件上传/下载/删除的基础操作。
 *
 * <p>当前实现见 {@link MinioFileStorageService}。后续可扩展其他存储后端
 * （如 OSS、S3、本地文件系统），调用方仅依赖本接口，无需改动。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
public interface FileStorageService {

    /**
     * 上传文件到存储服务。
     *
     * @param inputStream 文件输入流
     * @param objectKey   对象键（路径）
     * @param contentType 文件 MIME 类型
     */
    void uploadFile(InputStream inputStream, String objectKey, String contentType);

    /**
     * 从存储服务下载文件。
     *
     * @param objectKey 对象键
     * @return 文件输入流（调用方负责关闭）
     */
    InputStream getFile(String objectKey);

    /**
     * 从存储服务删除文件。
     *
     * @param objectKey 对象键
     */
    void deleteFile(String objectKey);

    /**
     * 根据对象键构建完整文件访问 URL。
     *
     * @param objectKey 对象键（如 textbooks/uuid.pdf）
     * @return 完整文件访问路径
     */
    String getFileUrl(String objectKey);

    /**
     * 从完整文件访问 URL 中提取对象键。
     *
     * @param filePath 完整文件路径
     * @return 对象键，无法提取时返回原值
     */
    String extractObjectKey(String filePath);
}
