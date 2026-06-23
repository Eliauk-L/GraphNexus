package com.graphnexus.application.system.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查指示器。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    @Override
    public Health health() {
        try {
            long start = System.currentTimeMillis();
            connectionFactory.getConnection().ping();
            long latency = System.currentTimeMillis() - start;
            log.debug("Redis 健康检查通过，延迟: {}ms", latency);
            return Health.up().withDetail("latency", latency).build();
        } catch (Exception e) {
            log.warn("Redis 健康检查失败: {}", e.getMessage());
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}