package com.graphnexus.application.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private int maxRounds = 6;
    private long totalTimeoutMs = 30000;
    private int maxObservationChars = 12000;
    private int maxConsecutiveErrors = 2;
}
