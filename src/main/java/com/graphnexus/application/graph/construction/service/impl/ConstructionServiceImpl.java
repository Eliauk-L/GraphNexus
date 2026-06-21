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
import com.graphnexus.infrastructure.neo4j.node.SubjectNode;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * <p>Neo4j 写入不在 Spring {@code @Transactional} 范围内。
 * 阶段二融合原子性由融合服务内部 Neo4j 事务保证（见 ADR-020）。</p>
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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 执行两阶段流水线：图谱构建 → 图谱融合。
     */
    @Override
    @Transactional
    public ExtractionResultBO extract(Long documentId) {
        TextbookDO doc = textbookRepository.findByIdAndStatusNot(documentId, FileStatus.DELETING)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: " + documentId));

        validateDocStatus(doc);
        doc.setStatus(FileStatus.EXTRACTING);
        textbookRepository.saveAndFlush(doc);

        String neo4jDocumentId = String.valueOf(documentId);
        String subjectName = doc.getSubject();

        // LLM 抽取（含单文档内 Entity→KP 的 ALIGNED_TO 边）
        ExtractionService.ExtractionResult extracted = extractionService.extract(
                doc.getTextContent(), doc.getName(), subjectName,
                doc.getPageCount(), neo4jDocumentId);

        SubjectNode subjectNode = constructionGraphRepository.findOrCreateSubject(subjectName);

        // ==================== 阶段一：图谱构建 ====================
        ExtractionResultBO result = phase1_build(doc, extracted, subjectNode, neo4jDocumentId);

        // 图结构已变更（新节点/边已写入 Neo4j），发布事件触发指标缓存失效
        eventPublisher.publishEvent(new GraphChangedEvent(this));

        // ==================== 阶段二：图谱融合（事件驱动） ====================
        // 跨文档实体对齐由融合隐式完成：KP 合并时 ALIGNED_TO 边自动重定向到规范 KP
        List<String> affectedKpNames = extracted.knowledgePoints().stream()
                .map(kp -> kp.getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 发布 GraphConstructedEvent（替代直接调用融合服务 · DESIGN D5/D11）
        eventPublisher.publishEvent(new GraphConstructedEvent(
                this, GraphConstructedEvent.SOURCE_DOCUMENT, GraphConstructedEvent.MODE_INCREMENTAL,
                doc.getSubject(), affectedKpNames, documentId, null));
        // 重读文档状态（监听器同 tx JPA 一级缓存，同一托管实例 · D3）
        if (doc.getStatus() == FileStatus.EXTRACTED && doc.getFailReason() != null) {
            result.setFusionWarning(doc.getFailReason());
        }

        return result;
    }

    /**
     * 阶段一：图谱构建 — 单文档内节点/边 + SubjectNode 写入 Neo4j。
     */
    private ExtractionResultBO phase1_build(TextbookDO doc, ExtractionService.ExtractionResult extracted,
                                            SubjectNode subjectNode, String neo4jDocumentId) {
        FileNode documentNode = new FileNode(doc.getName(), doc.getPageCount(), neo4jDocumentId);

        constructionGraphRepository.deleteByDocumentId(neo4jDocumentId);
        constructionGraphRepository.save(documentNode);
        constructionGraphRepository.saveEdge(new BelongsToSubjectEdge(documentNode.getId(), subjectNode.getId()));

        for (var entity : extracted.entities()) {
            constructionGraphRepository.save(entity);
            constructionGraphRepository.saveEdge(new ExtractsEdge(documentNode.getId(), entity.getId()));
        }
        for (var kp : extracted.knowledgePoints()) {
            constructionGraphRepository.save(kp);
            constructionGraphRepository.saveEdge(new BelongsToSubjectEdge(kp.getId(), subjectNode.getId()));
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

        doc.setStatus(FileStatus.EXTRACTED);
        textbookRepository.saveAndFlush(doc);
        return result;
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
        return GraphSubgraphBO.builder()
                .nodes(nodes.stream().map(GraphDataConverter::toNodeData).collect(Collectors.toList()))
                .edges(edges.stream().map(GraphDataConverter::toEdgeData).collect(Collectors.toList()))
                .build();
    }
}