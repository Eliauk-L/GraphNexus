package com.graphnexus.application.analysis.fusion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 宽图谱融合配置属性 — 绑定 application-dev.yml 中 {@code fusion.*} 配置项。
 *
 * <p><b>策略专属参数不在此类</b>——每个策略独立绑定自己的
 * {@code @ConfigurationProperties} 前缀（如 {@link FuzzyMatchProperties}、
 * {@link TimeDecayProperties}）。新增策略只需新建配置类 + 实现接口，
 * 无需修改本类。见 DESIGN 方案 B。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Component
@ConfigurationProperties(prefix = "fusion")
public class FusionProperties {

    /** KP 匹配策略选择 */
    private KpMatching matching = new KpMatching();

    /** 权重计算策略选择 */
    private Weight weight = new Weight();

    @Data
    public static class KpMatching {
        /** 策略名称：fuzzy / exact / vector-similarity（未来） */
        private String strategy = "fuzzy";

        /** 融合阈值（0~1） */
        private double threshold = 0.85;
    }

    @Data
    public static class Weight {
        /** 策略名称：time-decay / simple-average / ewma（未来） */
        private String strategy = "time-decay";
    }
}