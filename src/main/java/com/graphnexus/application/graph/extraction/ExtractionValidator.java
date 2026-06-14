package com.graphnexus.application.graph.extraction;

import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.node.EntityType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM 抽取结果校验器 — 对 {@link ExtractionRawResult} 执行结构和语义校验。
 *
 * <p>校验规则对应 AC-3：entityType 枚举值检查、relationshipType 枚举值检查、
 * 索引不越界、必填字段非空。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Slf4j
@Component
public class ExtractionValidator {

    private static final Set<String> VALID_ENTITY_TYPES =
            Arrays.stream(EntityType.values()).map(EntityType::getValue).collect(Collectors.toSet());

    private static final Set<String> VALID_RELATION_TYPES =
            Set.of("DERIVES", "CONTAINS", "REFERENCES");

    /**
     * 校验 LLM 返回的原始抽取结果。
     *
     * @param raw LLM 反序列化后的原始结果
     * @throws BusinessException(A0010) 校验失败时抛出，message 含具体字段名和违规值
     */
    public void validate(ExtractionRawResult raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.A0010, "抽取结果为空");
        }

        validateEntities(raw);
        validateEntityRelations(raw);
        validateAlignments(raw);
        validateCategories(raw);

        log.debug("抽取结果校验通过：entities={}, knowledgePoints={}, categories={}",
                raw.getEntities() != null ? raw.getEntities().size() : 0,
                raw.getKnowledgePoints() != null ? raw.getKnowledgePoints().size() : 0,
                raw.getCategories() != null ? raw.getCategories().size() : 0);
    }

    private void validateEntities(ExtractionRawResult raw) {
        if (raw.getEntities() == null || raw.getEntities().isEmpty()) {
            throw new BusinessException(ErrorCode.A0010, "entities 数组为空或缺失");
        }
        for (int i = 0; i < raw.getEntities().size(); i++) {
            ExtractionRawResult.RawEntity entity = raw.getEntities().get(i);
            if (!StringUtils.hasText(entity.getName())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entities[%d].name 为空", i));
            }
            if (!StringUtils.hasText(entity.getOriginalText())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entities[%d].originalText 为空", i));
            }
            if (!StringUtils.hasText(entity.getEntityType())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entities[%d].entityType 为空", i));
            }
            if (!VALID_ENTITY_TYPES.contains(entity.getEntityType())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entities[%d].entityType='%s' 不在合法枚举值 %s 中",
                                i, entity.getEntityType(), VALID_ENTITY_TYPES));
            }
        }
    }

    private void validateEntityRelations(ExtractionRawResult raw) {
        if (raw.getEntityRelations() == null) {
            return; // 实体间关系可选
        }
        int entityCount = raw.getEntities().size();
        for (int i = 0; i < raw.getEntityRelations().size(); i++) {
            ExtractionRawResult.RawEntityRelation rel = raw.getEntityRelations().get(i);
            if (rel.getSourceEntityIndex() == null || rel.getSourceEntityIndex() < 0
                    || rel.getSourceEntityIndex() >= entityCount) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entityRelations[%d].sourceEntityIndex=%s 越界（共 %d 个实体）",
                                i, rel.getSourceEntityIndex(), entityCount));
            }
            if (rel.getTargetEntityIndex() == null || rel.getTargetEntityIndex() < 0
                    || rel.getTargetEntityIndex() >= entityCount) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entityRelations[%d].targetEntityIndex=%s 越界（共 %d 个实体）",
                                i, rel.getTargetEntityIndex(), entityCount));
            }
            if (!StringUtils.hasText(rel.getType())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entityRelations[%d].type 为空", i));
            }
            if (!VALID_RELATION_TYPES.contains(rel.getType())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("entityRelations[%d].type='%s' 不在合法枚举值 %s 中",
                                i, rel.getType(), VALID_RELATION_TYPES));
            }
        }
    }

    private void validateAlignments(ExtractionRawResult raw) {
        if (raw.getAlignments() == null) {
            return;
        }
        int entityCount = raw.getEntities().size();
        int kpCount = raw.getKnowledgePoints() != null ? raw.getKnowledgePoints().size() : 0;
        for (int i = 0; i < raw.getAlignments().size(); i++) {
            ExtractionRawResult.RawAlignment align = raw.getAlignments().get(i);
            if (align.getEntityIndex() == null || align.getEntityIndex() < 0
                    || align.getEntityIndex() >= entityCount) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("alignments[%d].entityIndex=%s 越界", i, align.getEntityIndex()));
            }
            if (align.getKnowledgePointIndex() == null || align.getKnowledgePointIndex() < 0
                    || align.getKnowledgePointIndex() >= kpCount) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("alignments[%d].knowledgePointIndex=%s 越界", i, align.getKnowledgePointIndex()));
            }
        }
    }

    private void validateCategories(ExtractionRawResult raw) {
        if (raw.getCategories() == null) {
            return;
        }
        for (int i = 0; i < raw.getCategories().size(); i++) {
            ExtractionRawResult.RawCategory cat = raw.getCategories().get(i);
            if (!StringUtils.hasText(cat.getName())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("categories[%d].name 为空", i));
            }
        }
    }
}