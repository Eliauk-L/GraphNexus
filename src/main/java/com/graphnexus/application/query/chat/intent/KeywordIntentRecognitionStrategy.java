package com.graphnexus.application.query.chat.intent;

import com.graphnexus.application.query.chat.model.QueryIntent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 关键词意图识别策略 — 基于中文关键词匹配，作为 LLM 分类的降级兜底。
 *
 * <p>Priority=20（LLM 策略之后执行）。
 * v2 新增意图只需在 {@link #buildKeywordMap()} 中追加关键词条目。</p>
 *
 * @author Jay
 * @date 2026/06/22
 */
@Component
public class KeywordIntentRecognitionStrategy implements IntentRecognitionStrategy {

    private final Map<String, QueryIntent> keywordMap;

    public KeywordIntentRecognitionStrategy() {
        this.keywordMap = buildKeywordMap();
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public QueryIntent recognize(String question) {
        if (question == null || question.isBlank()) return null;
        for (var entry : keywordMap.entrySet()) {
            if (question.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 构建关键词 → 意图映射表。
     * v2 新增意图时在此方法追加条目即可。
     */
    private static Map<String, QueryIntent> buildKeywordMap() {
        Map<String, QueryIntent> map = new LinkedHashMap<>();
        // STUDENT_DIAGNOSIS 关键词（优先匹配，含学生姓名的问题先命中此类）
        map.put("薄弱", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("加强", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("掌握", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("诊断", QueryIntent.STUDENT_DIAGNOSIS);
        map.put("分析学生", QueryIntent.STUDENT_DIAGNOSIS);
        // CLASS_WEAKNESS_OVERVIEW 关键词（班级维度，放在学生关键词之后）
        map.put("全班", QueryIntent.CLASS_WEAKNESS_OVERVIEW);
        map.put("班级", QueryIntent.CLASS_WEAKNESS_OVERVIEW);
        map.put("某班", QueryIntent.CLASS_WEAKNESS_OVERVIEW);
        return map;
    }
}