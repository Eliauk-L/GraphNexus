package com.graphnexus.infrastructure.storage.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 *
 * <p>创建 {@link MinioClient} Bean，启动时自动检查并创建默认 Bucket。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private final MinioProperties minioProperties;

    /**
     * 创建 MinioClient Bean。
     *
     * @return 已配置的 MinioClient 实例
     */
    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();

        ensureBucketExists(client);
        return client;
    }

    /**
     * 确保默认 Bucket 存在，不存在则自动创建。
     */
    private void ensureBucketExists(MinioClient client) {
        try {
            String bucket = minioProperties.getBucket();
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO Bucket '{}' 已自动创建", bucket);
            } else {
                log.info("MinIO Bucket '{}' 已存在，跳过创建", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO Bucket 检查/创建失败，应用仍可启动。原因: {}", e.getMessage());
        }
    }
}