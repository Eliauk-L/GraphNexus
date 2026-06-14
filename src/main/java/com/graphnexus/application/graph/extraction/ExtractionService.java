package com.graphnexus.application.graph.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.llmgateway.service.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.edge.*;
import com.graphnexus.infrastructure.neo4j.node.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识图谱抽取编排服务 — 文本 → LLM → JSON Schema 校验 → 领域对象。
 *
 * <p>不负责写 Neo4j（由 GraphService 负责），只产出领域对象列表。
 * 流程见 DESIGN § 2.1 序列图。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final LlmGateway llmGateway;
    private final ExtractionPromptBuilder promptBuilder;
    private final ExtractionValidator validator;
    private final ObjectMapper objectMapper;

    /**
     * 从文档文本中抽取知识图谱。
     *
     * @param textContent PDF 解析后的文本内容
     * @param docName     文档名称
     * @param subject     学科
     * @param pageCount   页数
     * @param documentId  文档在 Neo4j 中的 ID（DocumentNode.id）
     * @return 抽取结果（含所有节点和边对象）
     */
    public ExtractionResult extract(String textContent, String docName, String subject,
                                     Integer pageCount, String documentId) {
        log.info("开始抽取知识图谱：docName={}, subject={}, 文本长度={}", docName, subject, textContent.length());

        // 1. 构建 Prompt
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userMessage = promptBuilder.buildUserMessage(docName, subject, pageCount, textContent);

        // 2. 调用 LLM
        String llmResponse = callLlmWithRetry(systemPrompt, userMessage);

        // 3. 预处理 + 反序列化
        ExtractionRawResult rawResult = parseResponse(llmResponse);

        // 4. 校验
        validator.validate(rawResult);

        // 5. 转换为领域对象
        ExtractionResult result = convertToDomain(rawResult, documentId);

        log.info("抽取完成：entities={}, knowledgePoints={}, categories={}, edges={}",
                result.entities().size(),
                result.knowledgePoints().size(),
                result.categories().size(),
                result.edges().size());
        return result;
    }

    /**
     * 调用 LLM，失败时重试一次。
     */
    private String callLlmWithRetry(String systemPrompt, String userMessage) {
        try {
            return llmGateway.chat(systemPrompt, userMessage);
        } catch (BusinessException e) {
            // 已包装为 C0001，直接上抛
            throw e;
        } catch (Exception e) {
            log.error("首次 LLM 调用失败，准备重试: {}", e.getMessage());
            try {
                String retryResponse = llmGateway.chat(systemPrompt, userMessage);
                log.info("重试成功");
                return retryResponse;
            } catch (Exception retryEx) {
                log.error("重试也失败: {}", retryEx.getMessage());
                throw new BusinessException(ErrorCode.C0001, "LLM 调用失败（已重试）: " + retryEx.getMessage());
            }
        }
    }

    /**
     * 预处理 LLM 响应并反序列化。
     */
    private ExtractionRawResult parseResponse(String llmResponse) {
        String cleaned = preprocessJson(llmResponse);
        try {
            return objectMapper.readValue(cleaned, ExtractionRawResult.class);
        } catch (JsonProcessingException e) {
            log.error("首次 JSON 解析失败，尝试去 markdown 包裹后重试: {}", e.getMessage());
            // 二次清理：更激进地去除可能的包裹
            String reCleaned = cleaned
                    .replaceAll("^[^{]*", "")
                    .replaceAll("[^}]*$", "");
            try {
                return objectMapper.readValue(reCleaned, ExtractionRawResult.class);
            } catch (JsonProcessingException ex) {
                log.error("二次解析也失败，原始响应前200字符: {}",
                        llmResponse.substring(0, Math.min(200, llmResponse.length())));
                throw new BusinessException(ErrorCode.A0010,
                        "LLM 返回非合法 JSON: " + ex.getOriginalMessage());
            }
        }
    }

    /**
     * 预处理 LLM 响应：去除 ```json ... ``` 包裹、首尾空白。
     */
    private String preprocessJson(String raw) {
        String cleaned = raw.trim();
        // 去除 ```json ... ``` 包裹
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf("\n");
            if (start > 0) {
                cleaned = cleaned.substring(start + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
            }
        }
        return cleaned.trim();
    }

    /**
     * 将校验通过的 RawResult 转换为 Neo4j 领域对象。
     */
    private ExtractionResult convertToDomain(ExtractionRawResult raw, String documentId) {
        List<EntityNode> entities = new ArrayList<>();
        List<KnowledgePointNode> knowledgePoints = new ArrayList<>();
        List<KnowledgeCategoryNode> categories = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        // 创建 Category 节点（先创建，因为 KP 需要引用的 category ID 在 BELONGS_TO 边里）
        List<String> categoryIds = new ArrayList<>();
        if (raw.getCategories() != null) {
            for (ExtractionRawResult.RawCategory rc : raw.getCategories()) {
                KnowledgeCategoryNode catNode = new KnowledgeCategoryNode(
                        rc.getName(), rc.getLevel(), rc.getParentName(), documentId);
                categories.add(catNode);
                categoryIds.add(catNode.getId());
            }
        }

        // 创建分类层次边（CHILD_OF）
        if (raw.getCategoryRelations() != null) {
            for (ExtractionRawResult.RawCategoryRelation rcr : raw.getCategoryRelations()) {
                if (rcr.getChildCategoryIndex() < categoryIds.size()
                        && rcr.getParentCategoryIndex() < categoryIds.size()) {
                    edges.add(new ChildOfEdge(
                            categoryIds.get(rcr.getChildCategoryIndex()),
                            categoryIds.get(rcr.getParentCategoryIndex())));
                }
            }
        }

        // 创建 KnowledgePoint 节点
        List<String> kpIds = new ArrayList<>();
        if (raw.getKnowledgePoints() != null) {
            for (ExtractionRawResult.RawKnowledgePoint rkp : raw.getKnowledgePoints()) {
                KnowledgePointNode kpNode = new KnowledgePointNode(
                        rkp.getName(), rkp.getDescription(), rkp.getSubject(),
                        rkp.getGradeLevel(), documentId);
                knowledgePoints.add(kpNode);
                kpIds.add(kpNode.getId());
            }
        }

        // 创建 Entity 节点
        List<String> entityIds = new ArrayList<>();
        for (ExtractionRawResult.RawEntity re : raw.getEntities()) {
            EntityNode entityNode = new EntityNode(
                    re.getEntityType(), re.getName(), re.getOriginalText(),
                    re.getPageNumber(), documentId, re.getMetadata());
            entities.add(entityNode);
            entityIds.add(entityNode.getId());
        }

        // 创建实体间关系边（DERIVES/CONTAINS/REFERENCES）
        if (raw.getEntityRelations() != null) {
            for (ExtractionRawResult.RawEntityRelation rer : raw.getEntityRelations()) {
                if (rer.getSourceEntityIndex() < entityIds.size()
                        && rer.getTargetEntityIndex() < entityIds.size()) {
                    edges.add(new ReferencesEdge(
                            entityIds.get(rer.getSourceEntityIndex()),
                            entityIds.get(rer.getTargetEntityIndex()),
                            rer.getType(),
                            rer.getDescription()));
                }
            }
        }

        // 创建对齐边（ALIGNED_TO）
        if (raw.getAlignments() != null) {
            for (ExtractionRawResult.RawAlignment ra : raw.getAlignments()) {
                if (ra.getEntityIndex() < entityIds.size()
                        && ra.getKnowledgePointIndex() < kpIds.size()) {
                    edges.add(new AlignedToEdge(
                            entityIds.get(ra.getEntityIndex()),
                            kpIds.get(ra.getKnowledgePointIndex())));
                }
            }
        }

        // 创建 KP→Category 归属边（BELONGS_TO）
        // 策略：如果只有一个 Category 且无 alignments 中的 categoryIndex，默认所有 KP 归属到该分类
        // 后续版本可从 LLM 输出中直接获取 kp→category 映射
        if (!categories.isEmpty() && !knowledgePoints.isEmpty()) {
            String targetCategoryId = categoryIds.get(categoryIds.size() - 1); // 最深分类
            for (String kpId : kpIds) {
                edges.add(new BelongsToEdge(kpId, targetCategoryId));
            }
        }

        // 创建前置依赖边（PREREQUISITE_OF）
        if (raw.getPrerequisites() != null) {
            for (ExtractionRawResult.RawPrerequisite rp : raw.getPrerequisites()) {
                if (rp.getSourceKnowledgePointIndex() < kpIds.size()
                        && rp.getTargetKnowledgePointIndex() < kpIds.size()) {
                    edges.add(new PrerequisiteEdge(
                            kpIds.get(rp.getSourceKnowledgePointIndex()),
                            kpIds.get(rp.getTargetKnowledgePointIndex()),
                            rp.getStrength(),
                            rp.getDescription()));
                }
            }
        }

        return new ExtractionResult(entities, knowledgePoints, categories, edges);
    }

    /**
     * 抽取结果 — 领域对象集合。
     */
    public record ExtractionResult(
            List<EntityNode> entities,
            List<KnowledgePointNode> knowledgePoints,
            List<KnowledgeCategoryNode> categories,
            List<GraphEdge> edges) {
    }
}