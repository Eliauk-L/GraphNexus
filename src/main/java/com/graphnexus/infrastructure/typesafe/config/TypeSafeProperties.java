package com.graphnexus.infrastructure.typesafe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TypeSafe System One（Jev）服务配置。
 *
 * <p>默认 OFF，避免在尚未完成来源记录与审核闭环前影响现有融合流程。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "typesafe")
public class TypeSafeProperties {

    private boolean enabled = false;
    private String baseUrl = "https://api.typesafe.ai";
    private String apiKey;
    private String model = "jev-1.13.0";
    private String questionVersion = "kp-alignment-v1";
    private int connectTimeoutMs = 2_000;
    private int readTimeoutMs = 5_000;
    private int maxRetries = 2;
    private long initialBackoffMs = 300;
    private int maxConcurrency = 4;
    private int maxCandidatesPerMention = 8;
    private long cacheMaxEntries = 1_000;
    private Mode mode = Mode.OFF;

    public boolean isActive() {
        return enabled && mode != Mode.OFF;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public enum Mode {
        OFF, SHADOW, REVIEW, AUTO
    }
}
