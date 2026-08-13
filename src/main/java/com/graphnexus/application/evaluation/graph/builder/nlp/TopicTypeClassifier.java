package com.graphnexus.application.evaluation.graph.builder.nlp;

import org.springframework.stereotype.Component;

@Component
public class TopicTypeClassifier {

    public String classify(String term, String context) {
        String value = (term == null ? "" : term) + " " + (context == null ? "" : context);
        if (containsAny(value, "图象", "图像", "表示", "坐标", "数轴", "表格", "示意图")) return "REPRESENTATIONAL";
        if (containsAny(value, "运算", "计算", "求解", "解法", "化简", "作图", "判定", "应用", "步骤")) return "PROCEDURAL";
        if (containsAny(value, "概念", "定义", "性质", "定理", "公式", "函数", "方程", "关系")) return "CONCEPTUAL";
        return null;
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
}
