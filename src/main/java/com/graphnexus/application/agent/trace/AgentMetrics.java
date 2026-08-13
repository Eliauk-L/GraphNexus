package com.graphnexus.application.agent.trace;

import com.graphnexus.application.agent.model.AgentResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AgentMetrics {
    private final MeterRegistry registry;

    public void record(AgentResponse response, long elapsedMs) {
        registry.counter("graphnexus.agent.tasks", "status", response.status()).increment();
        Timer.builder("graphnexus.agent.duration")
                .tag("status", response.status())
                .register(registry)
                .record(Duration.ofMillis(Math.max(0, elapsedMs)));
        registry.summary("graphnexus.agent.rounds", "status", response.status())
                .record(response.trace().size());
        if (response.fallbackReason() != null) {
            registry.counter("graphnexus.agent.fallbacks", "reason", response.fallbackReason()).increment();
        }
        response.trace().forEach(call -> {
            String outcome = call.observation().success() ? "success" : "failure";
            registry.counter("graphnexus.agent.tool.calls", "tool", call.toolName(), "outcome", outcome)
                    .increment();
            Timer.builder("graphnexus.agent.tool.duration")
                    .tag("tool", call.toolName()).tag("outcome", outcome)
                    .register(registry)
                    .record(Duration.ofMillis(Math.max(0, call.observation().metrics().elapsedMs())));
        });
    }
}
