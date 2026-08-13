package com.graphnexus.application.mastery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 掌握度更新配置，默认使用 alpha=0.3 的 EMA。 */
@Data
@Component
@ConfigurationProperties(prefix = "mastery")
public class MasteryProperties {

    private String strategy = "ema";
    private Ema ema = new Ema();
    private double weakThreshold = 0.6;
    private double masteredThreshold = 0.8;

    @Data
    public static class Ema {
        private double alpha = 0.3;
    }
}
