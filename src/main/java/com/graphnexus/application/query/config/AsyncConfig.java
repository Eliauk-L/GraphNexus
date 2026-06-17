package com.graphnexus.application.query.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置 — 为 QA 异步问答提供独立线程池。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private final QueryProperties queryProperties;

    public AsyncConfig(QueryProperties queryProperties) {
        this.queryProperties = queryProperties;
    }

    @Bean("queryAsyncExecutor")
    public Executor queryAsyncExecutor() {
        var props = queryProperties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix("query-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}