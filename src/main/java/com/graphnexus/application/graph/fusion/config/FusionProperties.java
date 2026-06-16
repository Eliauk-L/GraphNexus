package com.graphnexus.application.graph.fusion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 宽图谱融合配置属性 — 绑定 application-dev.yml 中 {@code fusion.*} 配置项。
 *
 * <p>所有算法参数（匹配权重/阈值/衰减因子/回滚容差）集中管理，
 * 通过 yml 实时调参无需重新编译。见 DESIGN D1/D2/D7。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Component
@ConfigurationProperties(prefix = "fusion")
public class FusionProperties {

    /** KP 匹配策略配置 */
    private KpMatching matching = new KpMatching();

    /** 权重计算策略配置 */
    private Weight weight = new Weight();

    /** 回滚配置 */
    private Rollback rollback = new Rollback();

    @Data
    public static class KpMatching {
        /** 策略名称：fuzzy（默认）/ exact */
        private String strategy = "fuzzy";

        /** 融合阈值（0~1），相似度 >= 此值归入同一融合组 */
        private double threshold = 0.85;

        /** 模糊匹配子配置 */
        private Fuzzy fuzzy = new Fuzzy();

        @Data
        public static class Fuzzy {
            /** 字符 Jaccard 权重 */
            private double alpha = 0.3;

            /** Bigram Jaccard 权重 */
            private double beta = 0.5;

            /** 归一化编辑距离权重 */
            private double gamma = 0.2;
        }
    }

    @Data
    public static class Weight {
        /** 策略名称：time-decay（默认）/ simple-average */
        private String strategy = "time-decay";

        /** 时间衰减子配置 */
        private TimeDecay timeDecay = new TimeDecay();

        @Data
        public static class TimeDecay {
            /** 月衰减因子（0~1） */
            private double factor = 0.9;
        }
    }

    @Data
    public static class Rollback {
        /** MASTERS weight 一致性校验容差 */
        private double weightTolerance = 0.01;
    }
}