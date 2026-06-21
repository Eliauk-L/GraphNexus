package com.graphnexus.application.analysis.fusion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模糊匹配策略专属配置 — 绑定 {@code fusion.strategy.fuzzy.*} 配置项。
 *
 * <p>独立于 {@link FusionProperties}，新增匹配策略只需新建自己的
 * {@code @ConfigurationProperties} 类，无需修改任何既有代码。
 * 见方案 B 设计。</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Data
@Component
@ConfigurationProperties(prefix = "fusion.strategy.fuzzy")
public class FuzzyMatchProperties {

    /** 字符 Jaccard 权重（默认 0.3） */
    private double alpha = 0.3;

    /** Bigram Jaccard 权重（默认 0.5） */
    private double beta = 0.5;

    /** 归一化编辑距离权重（默认 0.2） */
    private double gamma = 0.2;
}