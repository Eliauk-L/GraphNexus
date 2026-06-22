package com.graphnexus.application.graph.construction.extract;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.graph.construction.extract.registry.EntityRelationType;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionEdgeFactoryRegistry;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandler;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandlerRegistry;
import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.common.LlmGateway;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.edge.*;
import com.graphnexus.infrastructure.neo4j.node.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final ExtractionJsonParser jsonParser;
    private final ExtractionEdgeFactoryRegistry edgeFactoryRegistry;
    private final ExtractionNodeHandlerRegistry nodeHandlerRegistry;

    /**
     * 用于扩展段 {@code Map → Raw POJO} 的类型转换（D4）。
     * 复用 ExtractionJsonParser 的宽松 ObjectMapper 风格——构造一个独立宽松实例避免循环依赖。
     */
    private final ObjectMapper convertMapper = new ObjectMapper();

    /**
     * 从文档文本中抽取知识图谱。
     *
     * @param textContent PDF 解析后的文本内容
     * @param docName     文档名称
     * @param subject     学科
     * @param pageCount   页数
     * @param documentId  文档在 Neo4j 中的 ID（FileNode.id）
     * @return 抽取结果（含所有节点和边对象）
     */
    public ExtractionResult extract(String textContent, String docName, String subject,
                                     Integer pageCount, String documentId) {
        log.info("开始抽取知识图谱：docName={}, subject={}, 文本长度={}", docName, subject, textContent.length());

        // 1. 构建 Prompt（重试时不变）— subject 用于 few-shot 分域（D5）
        String systemPrompt = promptBuilder.buildSystemPrompt(subject);
        String userMessage = promptBuilder.buildUserMessage(docName, subject, pageCount, textContent);

        // 2. LLM 调用 + JSON 解析 — 作为可重试单元
        ExtractionRawResult rawResult = callAndParseWithRetry(systemPrompt, userMessage);

        // 3. 校验
        validator.validate(rawResult);

        // 4. 转换为领域对象
        ExtractionResult result = convertToDomain(rawResult, documentId);

        // 前置依赖覆盖度检测：覆盖率 < 30% 记录 warn 日志
        int kpCount = result.knowledgePoints().size();
        long prereqCount = result.edges().stream()
                .filter(e -> "PREREQUISITE_OF".equals(e.getEdgeType()))
                .count();
        if (kpCount > 3 && prereqCount < kpCount * 0.3) {
            log.warn("前置依赖提取不足：{} 个知识点仅 {} 条前置依赖（覆盖率 {:.0f}%），"
                    + "建议检查 prompt 或 LLM 输出质量",
                    kpCount, prereqCount, (double) prereqCount / kpCount * 100);
        }

        log.info("抽取完成：entities={}, knowledgePoints={}, categories={}, edges={}, prerequisites={}",
                result.entities().size(),
                result.knowledgePoints().size(),
                result.categories().size(),
                result.edges().size());
        return result;
    }

    /**
     * LLM 调用 + JSON 解析重试循环（最多 3 次）。
     * LLM 调用失败或 JSON 解析失败均触发重试，重试时在 prompt 中追加格式强调。
     */
    private ExtractionRawResult callAndParseWithRetry(String systemPrompt, String userMessage) {
        final int maxRetries = 3;
        Exception lastError = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            String prompt = attempt > 0
                    ? userMessage + "\n\n【重要提醒】上次返回的 JSON 格式无法解析。请严格输出合法 JSON，不要添加任何 markdown 代码块标记或注释。"
                    : userMessage;

            try {
                String llmResponse = llmGateway.chat(systemPrompt, prompt);
                ExtractionRawResult result = jsonParser.parse(llmResponse);
                if (attempt > 0) {
                    log.info("LLM JSON 解析重试成功（第{}次）", attempt + 1);
                }
                return result;
            } catch (BusinessException e) {
                lastError = e;
                if (attempt < maxRetries - 1) {
                    log.warn("LLM/JSON 处理失败（第{}/{}次），准备重试: {}", attempt + 1, maxRetries, e.getMessage());
                } else {
                    log.error("LLM/JSON 处理失败，已达最大重试次数({}): {}", maxRetries, e.getMessage());
                }
            } catch (Exception e) {
                lastError = e;
                if (attempt < maxRetries - 1) {
                    log.warn("LLM 调用异常（第{}/{}次），准备重试: {}", attempt + 1, maxRetries, e.getMessage());
                } else {
                    log.error("LLM 调用异常，已达最大重试次数({}): {}", maxRetries, e.getMessage());
                }
            }
        }

        throw new BusinessException(ErrorCode.C0001,
                "LLM 抽取失败（已重试" + maxRetries + "次）: " + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    /**
     * 将校验通过的 RawResult 转换为 Neo4j 领域对象。
     */
    private ExtractionResult convertToDomain(ExtractionRawResult raw, String documentId) {
        List<EntityNode> entities = new ArrayList<>();
        List<KnowledgePointNode> knowledgePoints = new ArrayList<>();
        List<KnowledgeCategoryNode> categories = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        List<GraphNode> extensionNodes = new ArrayList<>();

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

        // 创建实体间关系边 — 通过 ExtractionEdgeFactory 注册表路由，无 switch（D3 / AC-3）
        if (raw.getEntityRelations() != null) {
            for (ExtractionRawResult.RawEntityRelation rer : raw.getEntityRelations()) {
                if (rer.getSourceEntityIndex() < entityIds.size()
                        && rer.getTargetEntityIndex() < entityIds.size()) {
                    String srcId = entityIds.get(rer.getSourceEntityIndex());
                    String tgtId = entityIds.get(rer.getTargetEntityIndex());
                    String desc = rer.getDescription();
                    EntityRelationType type = EntityRelationType.fromValue(rer.getType());
                    if (type != null) {
                        edges.add(edgeFactoryRegistry.create(type, srcId, tgtId, desc));
                    }
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

        // 创建扩展节点 — 遍历 extensionSections，对每个注册 handler 反序列化→校验→转换（D4 / AC-4）
        // 现有 Entity/KP/Category 三类保持硬编码不动，本循环仅服务新增顶层节点类型
        for (ExtractionNodeHandler<?, ?> handler : nodeHandlerRegistry.all()) {
            Object section = raw.getExtensionSections() == null
                    ? null : raw.getExtensionSections().get(handler.sectionKey());
            if (section == null) {
                continue;
            }
            List<?> rawList = convertSectionToList(section, handler);
            if (rawList.isEmpty()) {
                continue;
            }
            List<GraphNode> converted = invokeHandler(handler, rawList, raw, documentId);
            extensionNodes.addAll(converted);
        }

        return new ExtractionResult(entities, knowledgePoints, categories, edges, extensionNodes);
    }

    /**
     * 把扩展段（JSON 数组反序列化后的 List&lt;Map&gt; 或单对象）转换为 handler 的 Raw POJO 列表。
     */
    @SuppressWarnings("rawtypes")
    private List<?> convertSectionToList(Object section, ExtractionNodeHandler handler) {
        List<?> asList;
        if (section instanceof List<?> list) {
            asList = list;
        } else {
            asList = List.of(section);
        }
        JavaType javaType = convertMapper.getTypeFactory()
                .constructCollectionType(List.class, handler.rawType());
        return convertMapper.convertValue(asList, javaType);
    }

    /**
     * 类型安全地调用 handler 的 validate + convert（绕过泛型类型擦除）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<GraphNode> invokeHandler(ExtractionNodeHandler handler, List rawList,
                                          ExtractionRawResult context, String documentId) {
        handler.validate(rawList, context);
        return (List<GraphNode>) handler.convert(rawList, documentId);
    }

    /**
     * 抽取结果 — 领域对象集合。
     */
    public record ExtractionResult(
            List<EntityNode> entities,
            List<KnowledgePointNode> knowledgePoints,
            List<KnowledgeCategoryNode> categories,
            List<GraphEdge> edges,
            List<GraphNode> extensionNodes) {

        /** 向后兼容：旧调用方不感知 extensionNodes 时用此构造 */
        public ExtractionResult(List<EntityNode> entities,
                                List<KnowledgePointNode> knowledgePoints,
                                List<KnowledgeCategoryNode> categories,
                                List<GraphEdge> edges) {
            this(entities, knowledgePoints, categories, edges, List.of());
        }
    }
}