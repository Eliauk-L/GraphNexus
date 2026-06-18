package com.graphnexus.application.query.chat.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryProperties 默认值验证（纯 Java 测试，不加载 Spring Context）。
 *
 * @author Jay
 * @date 2026/06/17
 */
class QueryPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        QueryProperties props = new QueryProperties();
        assertThat(props.getTokenBudget().getMaxInputTokens()).isEqualTo(8000);
        assertThat(props.getTokenBudget().getCharsPerToken()).isEqualTo(3);
        assertThat(props.getPruning().getWeakThreshold()).isEqualTo(0.6);
        assertThat(props.getPruning().getMaxPrerequisiteHops()).isEqualTo(2);
        assertThat(props.getRetry().getMaxRetries()).isEqualTo(2);
        assertThat(props.getRetry().getRetryDelayMs()).isEqualTo(1000);
        assertThat(props.getAsync().getCorePoolSize()).isEqualTo(2);
        assertThat(props.getAsync().getMaxPoolSize()).isEqualTo(5);
        assertThat(props.getAsync().getQueueCapacity()).isEqualTo(10);
        assertThat(props.getTimeout().getSyncTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void shouldSupportSetterOverrides() {
        QueryProperties props = new QueryProperties();
        props.getPruning().setWeakThreshold(0.4);
        props.getPruning().setMaxPrerequisiteHops(3);
        assertThat(props.getPruning().getWeakThreshold()).isEqualTo(0.4);
        assertThat(props.getPruning().getMaxPrerequisiteHops()).isEqualTo(3);
    }
}