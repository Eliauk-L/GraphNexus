package com.graphnexus.application.graph.metrics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图指标配置属性 —— 绑定 application-dev.yml 中 {@code graph.metrics.*} 配置项。
 *
 * <p>策略无关参数放此类，策略专属参数（未来新增指标）各自独立绑定。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Data
@Component
@ConfigurationProperties(prefix = "graph.metrics")
public class MetricsProperties {

    /** 缓存配置 */
    private Cache cache = new Cache();

    /** PageRank 算法参数 */
    private PageRank pageRank = new PageRank();

    /**
     * Caffeine 缓存配置。
     */
    public record Cache(
            int ttlMinutes,
            int maxSize
    ) {
        public Cache() {
            this(5, 50);
        }
    }

    /**
     * PageRank 算法参数。
     */
    public record PageRank(
            int maxIterations,
            double dampingFactor
    ) {
        public PageRank() {
            this(20, 0.85);
        }
    }
}