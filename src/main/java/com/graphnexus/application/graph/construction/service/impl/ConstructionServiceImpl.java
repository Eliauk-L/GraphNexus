package com.graphnexus.application.graph.construction.service.impl;

import com.graphnexus.application.graph.construction.model.ExtractionResultBO;
import com.graphnexus.application.graph.construction.model.GraphDataConverter;
import com.graphnexus.application.graph.construction.model.GraphSubgraphBO;
import com.graphnexus.application.graph.construction.extract.ExtractionService;
import com.graphnexus.application.graph.construction.service.ConstructionService;
import com.graphnexus.application.graph.fusion.config.FusionProperties;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.application.graph.fusion.model.KpCandidate;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.application.graph.fusion.strategy.KpMatchingStrategy;
import com.graphnexus.application.graph.metrics.event.GraphChangedEvent;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.edge.AlignedToEdge;
import com.graphnexus.infrastructure.neo4j.edge.BelongsToSubjectEdge;
import com.graphnexus.infrastructure.neo4j.edge.ExtractsEdge;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.node.EntityNode;
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
import java.util.Map;
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
    private final FusionProperties fusionProperties;
    private final Map<String, KpMatchingStrategy> matchingStrategies;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 执行三阶段流水线：构建→实体对齐→图谱融合。
     */
    @Override
    @Transactional
    public ExtractionResultBO extract(Long documentId) {
        TextbookDO doc = textbookRepository.findByIdAndIsDeletedAndStatusNot(documentId, 0, FileStatus.DELETING)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0006, "文档不存在: " + documentId));

        validateDocStatus(doc);
        doc.setStatus(FileStatus.EXTRACTING);
        textbookRepository.save(doc);

        String neo4jDocumentId = String.valueOf(documentId);
        String subjectName = doc.getSubject();

        // LLM 抽取（ExtractionResult 贯穿三阶段）
        ExtractionService.ExtractionResult extracted = extractionService.extract(
                doc.getTextContent(), doc.getName(), subjectName,
                doc.getPageCount(), neo4jDocumentId);

        SubjectNode subjectNode = constructionGraphRepository.findOrCreateSubject(subjectName);

        // ==================== 阶段一：图谱构建 ====================
        ExtractionResultBO result = phase1_build(doc, extracted, subjectNode, neo4jDocumentId);

        // ==================== 阶段二：实体对齐（跨文档） ====================
        phase2_align(doc, extracted, subjectNode, neo4jDocumentId);

        // ==================== 阶段三：图谱融合 ====================
        phase3_fuse(doc, extracted, result);

        eventPublisher.publishEvent(new GraphChangedEvent(this));
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
        textbookRepository.save(doc);
        return result;
    }

    /**
     * 阶段二：实体对齐 — 新 Entity 跨文档对齐到图谱中已有的 KP（见 ADR-022）。
     *
     * <p>对每个新抽取的 Entity，通过 {@link KpMatchingStrategy}（FuzzyMatch）匹配
     * 同一 SubjectNode 下的<b>其他文档</b>已有 KP 名称。命中则创建额外的
     * ALIGNED_TO 边（Entity → 已有 KP），使不同文档中讨论同一知识点的实体
     * 关联到统一 KP。对齐是非破坏性追加操作，失败不阻塞后续阶段。</p>
     */
    private void phase2_align(TextbookDO doc, ExtractionService.ExtractionResult extracted,
                              SubjectNode subjectNode, String neo4jDocumentId) {
        try {
            doc.setStatus(FileStatus.ALIGNING);
            textbookRepository.save(doc);

            if (extracted.entities().isEmpty()) {
                doc.setStatus(FileStatus.ALIGNED);
                textbookRepository.save(doc);
                return;
            }

            // 查询同 Subject 下已有的 KP（排除本文档新建的，避免自对齐）
            List<Map<String, Object>> existingKps = queryGraphRepository
                    .findKnowledgePointsBySubject(doc.getSubject()).stream()
                    .filter(kp -> !neo4jDocumentId.equals(kp.get("documentId")))
                    .collect(Collectors.toList());

            if (existingKps.isEmpty()) {
                log.debug("阶段二[实体对齐]：学科 {} 下无其他文档 KP，跳过", doc.getSubject());
                doc.setStatus(FileStatus.ALIGNED);
                textbookRepository.save(doc);
                return;
            }

            KpMatchingStrategy matcher = getMatchingStrategy();
            double threshold = fusionProperties.getMatching().getThreshold();
            String subjectName = doc.getSubject();

            // 将已有 KP 转为 KpCandidate（FuzzyMatch 要求两侧 subject 相同）
            List<KpCandidate> existingCandidates = existingKps.stream()
                    .map(kp -> new KpCandidate((String) kp.get("name"), subjectName,
                            (String) kp.get("documentId"), null))
                    .collect(Collectors.toList());

            int alignedCount = 0;
            for (EntityNode entity : extracted.entities()) {
                if (!StringUtils.hasText(entity.getName())) continue;
                KpCandidate entityCandidate = new KpCandidate(entity.getName(), subjectName, neo4jDocumentId, null);

                // 取相似度最高的已有 KP（≥ 阈值则对齐）
                String bestKpId = null;
                double bestScore = 0.0;
                for (int i = 0; i < existingCandidates.size(); i++) {
                    double score = matcher.match(entityCandidate, existingCandidates.get(i));
                    if (score > bestScore) {
                        bestScore = score;
                        bestKpId = (String) existingKps.get(i).get("id");
                    }
                }

                if (bestKpId != null && bestScore >= threshold) {
                    constructionGraphRepository.saveEdge(new AlignedToEdge(entity.getId(), bestKpId));
                    alignedCount++;
                }
            }

            doc.setStatus(FileStatus.ALIGNED);
            textbookRepository.save(doc);
            log.info("阶段二[实体对齐]完成：docId={}, 跨文档对齐 {} 个实体到已有 KP（阈值={}）",
                    doc.getId(), alignedCount, threshold);
        } catch (Exception e) {
            log.warn("阶段二[实体对齐]失败（不阻塞后续）: docId={}, {}", doc.getId(), e.getMessage());
            doc.setStatus(FileStatus.EXTRACTED);
            textbookRepository.save(doc);
        }
    }

    /**
     * 阶段三：图谱融合 — 跨源 KP 合并 + MASTERS 重算。
     *
     * <p>融合原子性由 FusionService 内部 Neo4j 事务保证（ADR-020）。
     * 失败不静默吞掉 — 标记 fusionWarning 且文档状态不进入 COMPLETED。</p>
     */
    private void phase3_fuse(TextbookDO doc, ExtractionService.ExtractionResult extracted,
                             ExtractionResultBO result) {
        List<String> affectedKpNames = extracted.knowledgePoints().stream()
                .map(kp -> kp.getName())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        try {
            fusionService.fuseIncremental(affectedKpNames, doc.getSubject());
            doc.setStatus(FileStatus.COMPLETED);
            textbookRepository.save(doc);
            log.info("阶段三[图谱融合]完成：docId={}, kps={}", doc.getId(), affectedKpNames.size());
        } catch (Exception e) {
            log.error("阶段三[图谱融合]失败，docId={}，可手动全量融合修复", doc.getId(), e);
            result.setFusionWarning("增量融合失败：" + e.getMessage() + "。可手动执行全量融合修复");
        }
    }

    private KpMatchingStrategy getMatchingStrategy() {
        String name = fusionProperties.getMatching().getStrategy();
        KpMatchingStrategy strategy = matchingStrategies.get(name);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.B0001,
                    "未找到 KP 匹配策略: " + name + "，可用: " + matchingStrategies.keySet());
        }
        return strategy;
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