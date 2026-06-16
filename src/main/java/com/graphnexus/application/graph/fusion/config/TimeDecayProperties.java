package com.graphnexus.application.graph.fusion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 时间衰减策略专属配置 — 绑定 {@code fusion.strategy.time-decay.*} 配置项。
 *
 * <p>独立于 {@link FusionProperties}，新增权重策略只需新建自己的
 * {@code @ConfigurationProperties} 类，无需修改任何既有代码。
 * 见方案 B 设计。</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Data
@Component
@ConfigurationProperties(prefix = "fusion.strategy.time-decay")
public class TimeDecayProperties {

    /** 月衰减因子（0~1，默认 0.9） */
    private double factor = 0.9;
}