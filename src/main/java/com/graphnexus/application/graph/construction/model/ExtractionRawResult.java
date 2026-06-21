package com.graphnexus.application.graph.construction.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 抽取结果的原始 JSON 映射 POJO — Jackson 反序列化目标。
 *
 * <p>对应 ADR-003 定义的 JSON Schema，包含 entities/knowledgePoints/categories 及各类关系数组。
 * 所有字段与 LLM 输出的 JSON key 严格对应。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
public class ExtractionRawResult {

    /** 实体列表 */
    private List<RawEntity> entities;

    /** 知识点列表 */
    private List<RawKnowledgePoint> knowledgePoints;

    /** 知识分类列表 */
    private List<RawCategory> categories;

    /** 实体→知识点对齐（entityIndex → knowledgePointIndex） */
    private List<RawAlignment> alignments;

    /** 实体间关系 */
    private List<RawEntityRelation> entityRelations;

    /** 知识点前置依赖 */
    private List<RawPrerequisite> prerequisites;

    /** 分类层次关系（childCategoryIndex → parentCategoryIndex） */
    private List<RawCategoryRelation> categoryRelations;

    /**
     * 扩展段容器 — 收集未映射到上述强类型字段的 JSON key（如新增顶层节点类型的段），
     * 供 {@link com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandler} 反序列化（D4 / ADR-023）。
     *
     * <p>与 ExtractionJsonParser 的 {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 兼容：
     * 加 @JsonAnySetter 后未知 key 从"忽略"变为"捕获"，已知 key 仍进强类型字段。</p>
     */
    private Map<String, Object> extensionSections = new LinkedHashMap<>();

    /**
     * Jackson 任意属性收集器 — 把未映射到强类型字段的 JSON key/value 存入 extensionSections。
     */
    @JsonAnySetter
    public void addExtensionSection(String key, Object value) {
        this.extensionSections.put(key, value);
    }

    // ---- 内部 POJO ----

    @Data
    public static class RawEntity {
        private String entityType;
        private String name;
        private String originalText;
        private Integer pageNumber;
        private Map<String, Object> metadata;
    }

    @Data
    public static class RawKnowledgePoint {
        private String name;
        private String description;
        private String subject;
        private String gradeLevel;
    }

    @Data
    public static class RawCategory {
        private String name;
        private String parentName;
        private Integer level;
    }

    @Data
    public static class RawAlignment {
        private Integer entityIndex;
        private Integer knowledgePointIndex;
    }

    @Data
    public static class RawEntityRelation {
        private Integer sourceEntityIndex;
        private Integer targetEntityIndex;
        private String type;
        private String description;
    }

    @Data
    public static class RawPrerequisite {
        private Integer sourceKnowledgePointIndex;
        private Integer targetKnowledgePointIndex;
        private Double strength;
        private String description;
    }

    @Data
    public static class RawCategoryRelation {
        private Integer childCategoryIndex;
        private Integer parentCategoryIndex;
    }
}