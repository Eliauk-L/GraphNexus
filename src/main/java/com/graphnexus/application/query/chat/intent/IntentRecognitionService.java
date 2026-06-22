package com.graphnexus.application.query.chat.intent;

import com.graphnexus.application.query.chat.model.QueryIntent;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 意图识别策略链编排器 — 注入所有 {@link IntentRecognitionStrategy} 实现，
 * 按 {@code priority} 升序链式执行，首个返回 non-null 的策略结果被采用。
 *
 * <p>全部策略返回 null → 抛 {@link BusinessException}(A0019)，
 * 提示用户无法识别查询意图。</p>
 *
 * @author Jay
 * @date 2026/06/22
 * @see IntentRecognitionStrategy
 */
@Slf4j
@Service
public class IntentRecognitionService {

    private final List<IntentRecognitionStrategy> strategies;

    /**
     * Spring 自动注入所有 {@link IntentRecognitionStrategy} 实现，
     * 按 priority 升序排序后缓存。
     */
    public IntentRecognitionService(List<IntentRecognitionStrategy> strategies) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(IntentRecognitionStrategy::priority))
                .toList();
        log.info("意图识别策略链已初始化 ({} 个策略): {}",
                this.strategies.size(),
                this.strategies.stream()
                        .map(s -> s.getClass().getSimpleName() + "(priority=" + s.priority() + ")")
                        .toList());
    }

    /**
     * 按优先级链式执行意图识别，首个 non-null 结果被采用。
     *
     * @param question 用户原始自然语言问题
     * @return 识别到的意图
     * @throws BusinessException(A0019) 所有策略均无法识别时
     */
    public QueryIntent recognize(String question) {
        for (IntentRecognitionStrategy strategy : strategies) {
            QueryIntent result = strategy.recognize(question);
            if (result != null) {
                log.debug("意图识别成功: strategy={}, intent={}",
                        strategy.getClass().getSimpleName(), result);
                return result;
            }
        }
        log.warn("所有意图识别策略均失败，无法识别查询意图: question={}", question);
        throw new BusinessException(ErrorCode.A0019,
                "无法识别查询意图，请更明确地描述问题。"
                + "当前支持：学生薄弱点诊断（如\"分析学生张三的数学薄弱点\"）");
    }
}