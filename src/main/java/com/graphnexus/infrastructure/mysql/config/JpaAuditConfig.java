package com.graphnexus.infrastructure.mysql.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 审计配置，启用 {@code @CreatedDate} / {@code @LastModifiedDate} 自动填充。
 *
 * @author Jay
 * @date 2026/06/12
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {
}