package com.graphnexus.infrastructure.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 连接配置属性。
 *
 * <p>绑定 application-dev.yml 中 {@code minio.*} 配置项。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 服务端点（例：http://localhost:9000） */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 密钥 */
    private String secretKey;

    /** 默认 Bucket 名称 */
    private String bucket;
}