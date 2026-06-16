package com.graphnexus.application.graph.service.impl;

import com.graphnexus.application.graph.extraction.ExtractionService;
import com.graphnexus.application.graph.model.ExtractionResultBO;
import com.graphnexus.application.graph.model.GraphSubgraphBO;
import com.graphnexus.application.graph.service.GraphService;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.document.DocumentDO;
import com.graphnexus.infrastructure.mysql.document.DocumentRepository;
import com.graphnexus.infrastructure.mysql.document.DocumentStatus;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.DocumentNode;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识图谱服务实现 — 编排抽取流程并管理 Neo4j 事务。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private final DocumentRepository documentRepository;
    private final ExtractionService extractionService;
    private final GraphNodeRepository graphNodeRepository;
    private final FusionService fusionService;

    @Override
    @Transactional
    public ExtractionResultBO extract(Long documentId) {
        // 1. 查询文档
        DocumentDO doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: " + documentId));

        // 2. 校验状态
        if (doc.getStatus() != DocumentStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.A0009,
                    String.format("文档状态为 %s，需先完成解析", doc.getStatus()));
        }

        // 3. 校验文本非空
        if (!StringUtils.hasText(doc.getTextContent())) {
            throw new BusinessException(ErrorCode.A0008, "文档文本内容为空");
        }

        // 4. 构建 DocumentNode
        String neo4jDocumentId = String.valueOf(documentId);
        DocumentNode documentNode = new DocumentNode(
                doc.getName(), doc.getSubject(),
                doc.getPageCount(), neo4jDocumentId);

        // 5. 调用 LLM 抽取
        ExtractionService.ExtractionResult extracted = extractionService.extract(
                doc.getTextContent(), doc.getName(), doc.getSubject(),
                doc.getPageCount(), neo4jDocumentId);

        // 6. 事务内：删旧子图 + 写新子图
        graphNodeRepository.deleteByDocumentId(neo4jDocumentId);
        graphNodeRepository.save(documentNode);
        // 建立 EXTRACTS 边：DocumentNode → 每个 EntityNode
        for (var entity : extracted.entities()) {
            graphNodeRepository.save(entity);
            graphNodeRepository.saveEdge(
                    new com.graphnexus.infrastructure.neo4j.edge.ExtractsEdge(documentNode.getId(), entity.getId()));
        }
        for (var kp : extracted.knowledgePoints()) {
            graphNodeRepository.save(kp);
        }
        for (var cat : extracted.categories()) {
            graphNodeRepository.save(cat);
        }
        graphNodeRepository.saveAllEdges(extracted.edges());

        // 7. 统计
        int totalEdges = extracted.edges().size() + extracted.entities().size(); // + EXTRACTS edges
        ExtractionResultBO result = ExtractionResultBO.builder()
                .documentId(documentId)
                .entityCount(extracted.entities().size())
                .knowledgePointCount(extracted.knowledgePoints().size())
                .categoryCount(extracted.categories().size())
                .edgeCount(totalEdges)
                .build();
        log.info("图谱抽取完成：docId={}, entities={}, kp={}, categories={}, edges={}",
                documentId, result.getEntityCount(), result.getKnowledgePointCount(),
                result.getCategoryCount(), result.getEdgeCount());

        // 增量融合（见 ADR-009）
        List<String> affectedKpNames = extracted.knowledgePoints().stream()
                .map(kp -> kp.getName())
                .collect(Collectors.toList());
        try {
            fusionService.fuseIncremental(affectedKpNames, doc.getSubject());
        } catch (Exception e) {
            log.error("增量融合失败（文档抽取后），documentId={}, kps={}，可手动全量融合修复",
                    documentId, affectedKpNames, e);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public GraphSubgraphBO getSubgraph(Long documentId) {
        String neo4jDocumentId = String.valueOf(documentId);
        List<GraphNode> nodes = graphNodeRepository.findByDocumentId(neo4jDocumentId);
        List<GraphEdge> edges = graphNodeRepository.findEdgesByDocumentId(neo4jDocumentId);
        return GraphSubgraphBO.builder()
                .nodes(nodes)
                .edges(edges)
                .build();
    }
}