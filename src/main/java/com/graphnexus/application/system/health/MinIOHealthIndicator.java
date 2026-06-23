package com.graphnexus.application.system.health;

import com.graphnexus.infrastructure.storage.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * MinIO 健康检查指示器。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinIOHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public Health health() {
        try {
            long start = System.currentTimeMillis();
            minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build()
            );
            long latency = System.currentTimeMillis() - start;
            log.debug("MinIO 健康检查通过，延迟: {}ms", latency);
            return Health.up().withDetail("latency", latency).build();
        } catch (Exception e) {
            log.warn("MinIO 健康检查失败: {}", e.getMessage());
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}