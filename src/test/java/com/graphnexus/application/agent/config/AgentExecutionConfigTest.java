package com.graphnexus.application.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.agent.service.AgentToolExecutor;
import com.graphnexus.application.agent.tool.TeachingToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void registersBusinessExecutorAndTaskExecutorWithDistinctBeanNames() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("agentToolExecutor");
            assertThat(context).hasBean("agentToolTaskExecutor");
            assertThat(context.getBean("agentToolExecutor")).isInstanceOf(AgentToolExecutor.class);
            assertThat(context.getBean("agentToolTaskExecutor")).isInstanceOf(ExecutorService.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AgentExecutionConfig.class)
    @ComponentScan(
            basePackageClasses = AgentToolExecutor.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = AgentToolExecutor.class))
    static class TestConfig {
        @Bean
        TeachingToolRegistry teachingToolRegistry() {
            return new TeachingToolRegistry(List.of());
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AgentProperties agentProperties() {
            return new AgentProperties();
        }
    }
}
