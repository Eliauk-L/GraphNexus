package com.graphnexus.application.graph.construction.service.impl;

import com.graphnexus.application.graph.construction.model.ExtractionResultBO;
import com.graphnexus.application.graph.construction.model.GraphDataConverter;
import com.graphnexus.application.graph.construction.model.GraphSubgraphBO;
import com.graphnexus.application.graph.construction.service.ConstructionService;
import com.graphnexus.application.graph.construction.service.ExtractionService;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.application.graph.metrics.event.GraphChangedEvent;
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
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
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
 * 图谱构建服务实现 — 编排三阶段流水线（构建→实体对齐→图谱融合）。
 *
 * <p>Neo4j 写入不在 Spring {@code @Transactional} 范围内。
 * 阶段三融合原子性由 FusionService 内部 Neo4j 事务保证（见 ADR-020）。</p>
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
    private final FusionService fusionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 执行三阶段流水线：构建→实体对齐→图谱融合。
     */
    @Override
    @Transactional
    public ExtractionResultBO extract(Long documentId) {
        TextbookDO doc = textbookRepository.findByIdAndIsDeletedAndStatusNot(documentId, 0, FileStatus.DELETING)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: " + documentId));

        // ==================== 阶段一：图谱构建 ====================
        validateDocStatus(doc);
        doc.setStatus(FileStatus.EXTRACTING);
        textbookRepository.save(doc);

        String neo4jDocumentId = String.valueOf(documentId);
        FileNode documentNode = new FileNode(doc.getName(), doc.getPageCount(), neo4jDocumentId);

        ExtractionService.ExtractionResult extracted = extractionService.extract(
                doc.getTextContent(), doc.getName(), doc.getSubject(),
                doc.getPageCount(), neo4jDocumentId);

        SubjectNode subjectNode = constructionGraphRepository.findOrCreateSubject(doc.getSubject());
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
                .documentId(documentId)
                .entityCount(extracted.entities().size())
                .knowledgePointCount(extracted.knowledgePoints().size())
                .categoryCount(extracted.categories().size())
                .edgeCount(totalEdges)
                .build();
        log.info("阶段一[图谱构建]完成：docId={}, entities={}, kp={}, categories={}, edges={}",
                documentId, result.getEntityCount(), result.getKnowledgePointCount(),
                result.getCategoryCount(), result.getEdgeCount());

        doc.setStatus(FileStatus.EXTRACTED);
        textbookRepository.save(doc);

        // ==================== 阶段二：实体对齐 ====================
        phase2_align(doc, subjectNode);

        // ==================== 阶段三：图谱融合 ====================
        List<String> affectedKpNames = extracted.knowledgePoints().stream()
                .map(kp -> kp.getName())
                .collect(Collectors.toList());
        try {
            fusionService.fuseIncremental(affectedKpNames, doc.getSubject());
            doc.setStatus(FileStatus.COMPLETED);
            textbookRepository.save(doc);
            log.info("阶段三[图谱融合]完成：docId={}, kps={}", documentId, affectedKpNames.size());
        } catch (Exception e) {
            log.error("阶段三[图谱融合]失败，documentId={}，可手动全量融合修复", documentId, e);
            result.setFusionWarning("增量融合失败：" + e.getMessage() + "。可手动执行全量融合修复");
        }

        eventPublisher.publishEvent(new GraphChangedEvent(this));
        return result;
    }

    /**
     * 阶段二：实体对齐 — 新 Entity 跨文档对齐到图谱已有 KP（见 ADR-022）。
     *
     * <p>对齐是非破坏性追加操作，失败不阻塞后续阶段。</p>
     */
    private void phase2_align(TextbookDO doc, SubjectNode subjectNode) {
        try {
            doc.setStatus(FileStatus.ALIGNING);
            textbookRepository.save(doc);
            // TODO: 实现跨文档实体对齐逻辑
            // 1. 查询同 SubjectNode 下已有 KPs（通过 QueryGraphRepository）
            // 2. 对每个新 Entity，通过 KpMatchingStrategy(FuzzyMatch) 匹配已有 KP
            // 3. 命中 → 创建 ALIGNED_TO 边
            doc.setStatus(FileStatus.ALIGNED);
            textbookRepository.save(doc);
            log.debug("阶段二[实体对齐]完成：docId={}", doc.getId());
        } catch (Exception e) {
            log.warn("阶段二[实体对齐]失败（不阻塞后续）: docId={}, {}", doc.getId(), e.getMessage());
            doc.setStatus(FileStatus.EXTRACTED);
            textbookRepository.save(doc);
        }
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