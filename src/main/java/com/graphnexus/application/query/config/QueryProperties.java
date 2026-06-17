package com.graphnexus.application.query.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 智能问答配置属性 — 绑定 application-dev.yml 中 {@code query.*} 配置项。
 *
 * <p>采用内嵌静态类组织分组配置，与 {@code FusionProperties} 模式一致。
 * 所有参数提供合理默认值，开发环境开箱即用。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Data
@Component
@ConfigurationProperties(prefix = "query")
public class QueryProperties {

    /** Token 预算控制 */
    private TokenBudget tokenBudget = new TokenBudget();

    /** 剪枝参数 */
    private Pruning pruning = new Pruning();

    /** LLM 调用重试 */
    private Retry retry = new Retry();

    /** 异步任务线程池 */
    private Async async = new Async();

    /** 同步请求超时 */
    private Timeout timeout = new Timeout();

    @Data
    public static class TokenBudget {
        /** LLM 输入 token 上限（默认 8000） */
        private int maxInputTokens = 8000;

        /** 字符/token 估算比例（保守近似：中文≈2, 英文≈4，取 3） */
        private int charsPerToken = 3;
    }

    @Data
    public static class Pruning {
        /** 弱掌握度阈值（0~1），weight 低于此值视为薄弱点 */
        private double weakThreshold = 0.6;

        /** PREREQUISITE_OF 最大遍历跳数 */
        private int maxPrerequisiteHops = 2;
    }

    @Data
    public static class Retry {
        /** 最大重试次数（不含首次调用） */
        private int maxRetries = 2;

        /** 重试间隔（毫秒） */
        private long retryDelayMs = 1000;
    }

    @Data
    public static class Async {
        /** 核心线程数 */
        private int corePoolSize = 2;

        /** 最大线程数 */
        private int maxPoolSize = 5;

        /** 队列容量 */
        private int queueCapacity = 10;
    }

    @Data
    public static class Timeout {
        /** 同步问答超时（秒） */
        private int syncTimeoutSeconds = 30;
    }
}