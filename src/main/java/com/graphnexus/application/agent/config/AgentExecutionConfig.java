package com.graphnexus.application.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AgentExecutionConfig {
    @Bean(name = "agentToolExecutor", destroyMethod = "shutdownNow")
    public ExecutorService agentToolExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "agent-tool-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
