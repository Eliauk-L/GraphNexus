package com.graphnexus.application.graph.construction.service.impl;

import com.graphnexus.application.graph.construction.extract.ExtractionService;
import com.graphnexus.application.graph.construction.event.GraphConstructedEvent;
import com.graphnexus.application.graph.construction.model.ExtractionResultBO;
import com.graphnexus.common.event.GraphChangedEvent;
import com.graphnexus.application.graph.construction.model.GraphDataConverter;
import com.graphnexus.application.graph.construction.model.GraphSubgraphBO;
import com.graphnexus.application.graph.construction.service.ConstructionService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.neo4j.edge.BelongsToSubjectEdge;
import com.graphnexus.infrastructure.neo4j.edge.ExtractsEdge;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.FileNode;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import com.graphnexus.infrastructure.neo4j.node.KnowledgePointNode;
import com.graphnexus.infrastructure.neo4j.node.SubjectNode;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 图谱构建服务实现 — 编排两阶段流水线（构建→图谱融合）。
 *
 * <p><b>跨文档实体对齐由融合隐式完成</b>：融合合并重复 KP 时，
 * {@code redirectEdges} 会把所有 {@code ALIGNED_TO} 边（Entity→KP）自动重定向到规范 KP，
 * 因此无需独立的跨文档对齐阶段。单文档内 Entity→KP 对齐由 LLM 抽取产出。</p>
 *
 * <p><b>事务策略（ADR-028）</b>：
 * <ul>
 *   <li>MySQL 状态变更：短事务（{@link TransactionTemplate} + JPA TM）</li>
 *   <li>LLM 抽取：无事务（ADR-028 规则 2）</li>
 *   <li>Neo4j 写入：独立 {@link TransactionTemplate} + {@link Neo4jTransactionManager}（ADR-028 规则 3）</li>
 *   <li>事件发布：事务外（ADR-028 规则 4）</li>
 *   <li>Neo4j 失败补偿：短事务回退 MySQL 状态（ADR-029）</li>
 * </ul>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConstructionServiceImpl implements ConstructionService {

    private final TextbookRepository textbookRepository;
    private final ExtractionService extractionService;
    private final ConstructionGraphRepository constructionGraphRepository;
    private final QueryGraphRepository queryGraphRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager txManager;
    private final Neo4jTransactionManager neo4jTransactionManager;

    /**
     * 执行两阶段流水线：图谱构建 → 图谱融合。
     *
     * <p>事务拆分（ADR-028）：短事务(EXTRACTING) → 无事务(LLM) → Neo4j Tx(构建) → 短事务(EXTRACTED) → 事务外事件。</p>
     */
    @Override
    public ExtractionResultBO extract(Long documentId) {
        // ===== 阶段 1：短事务 — 校验 + 状态→EXTRACTING（ADR-028 规则 1）=====

        TransactionTemplate jpaTx = new TransactionTemplate(txManager);
        String subjectName = jpaTx.execute(status -> {
            TextbookDO doc = textbookRepository.findByIdAndStatusNot(documentId, FileStatus.DELETING)
                    .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                            "文档不存在: " + documentId));

            validateDocStatus(doc);
            doc.setStatus(FileStatus.EXTRACTING);
            textbookRepository.save(doc);
            log.info("extract 触发: id={}, status→EXTRACTING", documentId);
            return doc.getSubject();
        });
        // EXTRACTING 已提交 → 前端轮询可见

        // ===== 阶段 2：无事务 — LLM 抽取（ADR-028 规则 2）=====

        TextbookDO docMeta = textbookRepository.findByIdAndStatusNot(documentId, FileStatus.DELETING)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: " + documentId));

        String neo4jDocumentId = String.valueOf(documentId);

        ExtractionService.ExtractionResult extracted;
        try {
            extracted = extractionService.extract(
                    docMeta.getTextContent(), docMeta.getName(), subjectName,
                    docMeta.getPageCount(), neo4jDocumentId);
        } catch (Exception e) {
            log.error("LLM 抽取失败: documentId={}, {}", documentId, e.getMessage());
            failExtraction(documentId, "LLM抽取失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.B0001, "LLM 抽取失败: " + e.getMessage());
        }

        // ===== 阶段 3：Neo4j 独立事务 — 阶段一构建（ADR-028 规则 3 + D10）=====

        TransactionTemplate neo4jTx = new TransactionTemplate(neo4jTransactionManager);
        ExtractionResultBO result;
        try {
            result = neo4jTx.execute(status -> {
                SubjectNode subjectNode = constructionGraphRepository.findOrCreateSubject(subjectName);
                return phase1_build(neo4jDocumentId, subjectName, subjectNode, extracted);
            });
        } catch (Exception e) {
            log.error("Neo4j 图谱构建失败: documentId={}, {}", documentId, e.getMessage());
            failExtraction(documentId, "图谱构建失败: " + e.getMessage());
            throw new BusinessException(ErrorCode.B0001, "图谱构建失败: " + e.getMessage());
        }

        // ===== 阶段 4：短事务 — 状态→EXTRACTED（ADR-028 规则 1）=====

        completeExtraction(documentId, result);

        // ===== 阶段 5：事务外发布事件（ADR-028 规则 4）=====

        eventPublisher.publishEvent(new GraphChangedEvent(this));

        List<String> affectedKpNames = extracted.knowledgePoints().stream()
                .map(kp -> kp.getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        eventPublisher.publishEvent(new GraphConstructedEvent(
                this, GraphConstructedEvent.SOURCE_DOCUMENT, GraphConstructedEvent.MODE_INCREMENTAL,
                subjectName, affectedKpNames, documentId, null));

        // 重读文档状态（GraphConstructedEventListener 可能已修改 · 事务外）
        TextbookDO finalDoc = textbookRepository.findById(documentId).orElse(null);
        if (finalDoc != null && finalDoc.getStatus() == FileStatus.EXTRACTED
                && finalDoc.getFailReason() != null) {
            result.setFusionWarning(finalDoc.getFailReason());
        }

        return result;
    }

    /**
     * 阶段一：图谱构建 — 单文档内节点/边 + SubjectNode 写入 Neo4j（在 Neo4j 事务内执行）。
     */
    private ExtractionResultBO phase1_build(String neo4jDocumentId, String subjectName,
                                            SubjectNode subjectNode,
                                            ExtractionService.ExtractionResult extracted) {
        // Re-fetch doc for name/pageCount (inside Neo4j tx, no JPA tx context)
        TextbookDO doc = textbookRepository.findByIdAndStatusNot(
                Long.parseLong(neo4jDocumentId), FileStatus.DELETING)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006,
                        "文档不存在: " + neo4jDocumentId));

        FileNode documentNode = new FileNode(doc.getName(), doc.getPageCount(), neo4jDocumentId);

        constructionGraphRepository.deleteByDocumentId(neo4jDocumentId);
        constructionGraphRepository.save(documentNode);
        constructionGraphRepository.saveEdge(new BelongsToSubjectEdge(documentNode.getId(), subjectNode.getId()));

        for (var entity : extracted.entities()) {
            constructionGraphRepository.save(entity);
            constructionGraphRepository.saveEdge(new ExtractsEdge(documentNode.getId(), entity.getId()));
        }
        for (var kp : extracted.knowledgePoints()) {
            // 查找同 Subject 下同名 KP（可能来自考试上传），复用其节点避免重复
            KnowledgePointNode existing = constructionGraphRepository.findExistingKnowledgePoint(
                    kp.getName(), subjectName);
            if (existing != null) {
                // 复用已有节点 id，同时更新文档属性（文档数据质量更高）
                kp.setId(existing.getId());
            }
            constructionGraphRepository.save(kp);
            // BELONGS_TO_SUBJECT 边已在已有节点上存在（或由 save 后的 saveEdge 创建）
            if (existing == null) {
                constructionGraphRepository.saveEdge(new BelongsToSubjectEdge(kp.getId(), subjectNode.getId()));
            }
        }
        for (var cat : extracted.categories()) {
            constructionGraphRepository.save(cat);
        }
        constructionGraphRepository.saveAllEdges(extracted.edges());

        int subjectEdges = 1 + extracted.knowledgePoints().size();
        int totalEdges = extracted.edges().size() + extracted.entities().size() + subjectEdges;
        ExtractionResultBO result = ExtractionResultBO.builder()
                .documentId(doc.getId())
                .entityCount(extracted.entities().size())
                .knowledgePointCount(extracted.knowledgePoints().size())
                .categoryCount(extracted.categories().size())
                .edgeCount(totalEdges)
                .build();
        log.info("阶段一[图谱构建]完成：docId={}, entities={}, kp={}, edges={}",
                doc.getId(), result.getEntityCount(), result.getKnowledgePointCount(), result.getEdgeCount());

        return result;
    }

    /**
     * 短事务：抽取失败 → 回退状态到 PARSED + failReason（ADR-029 跨存储补偿）。
     */
    private void failExtraction(Long documentId, String reason) {
        TransactionTemplate jpaTx = new TransactionTemplate(txManager);
        jpaTx.executeWithoutResult(status -> {
            textbookRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(FileStatus.PARSED);
                doc.setFailReason(truncate(reason, 300));
                textbookRepository.save(doc);
                log.info("抽取失败，状态回退 PARSED: id={}, reason={}", documentId, reason);
            });
        });
    }

    /**
     * 短事务：图谱构建完成 → 状态 EXTRACTED（ADR-028 规则 1）。
     */
    private void completeExtraction(Long documentId, ExtractionResultBO result) {
        TransactionTemplate jpaTx = new TransactionTemplate(txManager);
        jpaTx.executeWithoutResult(status -> {
            textbookRepository.findById(documentId).ifPresent(doc -> {
                doc.setStatus(FileStatus.EXTRACTED);
                textbookRepository.save(doc);
            });
        });
    }

    private void validateDocStatus(TextbookDO doc) {
        FileStatus status = doc.getStatus();
        if (status != FileStatus.PARSED && status != FileStatus.EXTRACTED
                && status != FileStatus.COMPLETED && status != FileStatus.EXTRACTING) {
            throw new BusinessException(ErrorCode.A0009,
                    String.format("文档状态为 %s，需先完成解析", status));
        }
        if (!StringUtils.hasText(doc.getTextContent())) {
            throw new BusinessException(ErrorCode.A0008, "文档文本内容为空");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GraphSubgraphBO getSubgraph(Long documentId) {
        String neo4jDocumentId = String.valueOf(documentId);
        List<GraphNode> nodes = constructionGraphRepository.findByDocumentId(neo4jDocumentId);
        List<GraphEdge> edges = constructionGraphRepository.findEdgesByDocumentId(neo4jDocumentId);
        return buildSubgraphResult(nodes, edges);
    }

    @Override
    @Transactional(readOnly = true)
    public GraphSubgraphBO getFullGraph() {
        List<GraphNode> nodes = constructionGraphRepository.findAllNodes();
        List<GraphEdge> edges = constructionGraphRepository.findAllEdges();
        log.info("全量图谱查询完成：nodes={}, edges={}", nodes.size(), edges.size());
        return buildSubgraphResult(nodes, edges);
    }

    @Override
    public List<String> listSubjects() {
        return queryGraphRepository.findDistinctSubjects();
    }

    @Override
    @Transactional(readOnly = true)
    public GraphSubgraphBO getSubjectGraph(String subjectName) {
        long start = System.currentTimeMillis();
        List<GraphNode> nodes = constructionGraphRepository.findBySubject(subjectName);
        long nodeMs = System.currentTimeMillis() - start;
        List<GraphEdge> edges = constructionGraphRepository.findEdgesBySubject(subjectName);
        long edgeMs = System.currentTimeMillis() - start - nodeMs;
        log.info("学科全景图查询完成：subjectName={}, nodes={}, edges={}, nodeMs={}, edgeMs={}",
                subjectName, nodes.size(), edges.size(), nodeMs, edgeMs);
        return buildSubgraphResult(nodes, edges);
    }

    private GraphSubgraphBO buildSubgraphResult(List<GraphNode> nodes, List<GraphEdge> edges) {
        return GraphSubgraphBO.builder()
                .nodes(nodes.stream().map(GraphDataConverter::toNodeData).collect(Collectors.toList()))
                .edges(edges.stream().map(GraphDataConverter::toEdgeData).collect(Collectors.toList()))
                .build();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}