package com.graphnexus.application.query.chat.intent;

import com.graphnexus.application.query.chat.model.QueryIntent;

/**
 * 可插拔意图识别策略接口 — 从用户自然语言问题中识别 {@link QueryIntent}。
 *
 * <p>实现类通过 Spring {@code @Component} 自动注册到
 * {@link IntentRecognitionService} 的策略链中。
 * 策略按 {@link #priority()} 升序链式执行，首个返回 non-null 的策略结果被采用。</p>
 *
 * <p>返回 {@code null} 表示本策略无法判定，交由链中下一策略处理。
 * 内置策略 priority 使用 1-99，扩展策略从 100 起。</p>
 *
 * @author Jay
 * @date 2026/06/22
 * @see IntentRecognitionService
 */
public interface IntentRecognitionStrategy {

    /**
     * 尝试从用户问题中识别意图。
     *
     * @param question 用户原始自然语言问题（非空）
     * @return 识别到的意图；若本策略无法判定则返回 {@code null}
     */
    QueryIntent recognize(String question);

    /**
     * 策略优先级（越小越先执行）。
     * 内置策略占用 1-99，扩展策略从 100 起。
     *
     * @return 优先级数值，默认 100
     */
    default int priority() {
        return 100;
    }
}