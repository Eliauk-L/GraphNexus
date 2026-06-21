package com.graphnexus.application.analysis.fusion.event;

import com.graphnexus.application.analysis.fusion.service.FusionService;
import com.graphnexus.application.graph.construction.event.GraphConstructedEvent;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 图谱构建完成事件监听器 —— 同步消费 {@link GraphConstructedEvent}，触发下游融合。
 *
 * <p>消费方归 analysis 模块（ADR-024 依赖方向：construction(graph) → 事件 → fusion(analysis)）。
 * 同步 {@link EventListener}（join 发布方 Transactional，PROPAGATION_REQUIRED · DESIGN D2），
 * 无 Order 注解（DESIGN D5 · 唯一消费者）。</p>
 *
 * <p>按 {@code source} 分支处理：</p>
 * <ul>
 *   <li><b>DOCUMENT</b>（mode=INCREMENTAL）：文档状态机 FUSING→{COMPLETED, EXTRACTED+failReason}（D4）</li>
 *   <li><b>CSV</b>（mode=FULL）：仅 fuseFull，无文档状态机</li>
 * </ul>
 *
 * <p>融合失败被 try/catch 吞掉（不抛出，不回滚构建 · AC-9）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphConstructedEventListener {

    private final FusionService fusionService;
    private final TextbookRepository textbookRepository;

    /**
     * 消费图谱构建完成事件，触发融合。
     *
     * <p>同步执行（同线程），join 发布方 MySQL {@code @Transactional}。
     * DOCUMENT 路径通过 {@link TextbookRepository#findById} 获取与 {@code extract()} 同一托管实例（D3 · JPA 一级缓存）。</p>
     */
    @EventListener
    public void onGraphConstructed(GraphConstructedEvent event) {
        log.info("收到 GraphConstructedEvent: source={}, mode={}, subject={}, kpCount={}",
                event.getSource(), event.getMode(), event.getSubject(),
                event.getKpNames() != null ? event.getKpNames().size() : 0);

        if (GraphConstructedEvent.SOURCE_DOCUMENT.equals(event.getSource())) {
            handleDocumentPath(event);
        } else if (GraphConstructedEvent.SOURCE_CSV.equals(event.getSource())) {
            handleCsvPath(event);
        }
    }

    /**
     * DOCUMENT 路径：文档状态机 FUSING→fuseIncremental→{COMPLETED, EXTRACTED+failReason}。
     *
     * <p>状态机归属迁入监听器（DESIGN D4），失败回退 EXTRACTED+failReason，
     * 修复原 ConstructionServiceImpl 卡 FUSING 的 latent bug。</p>
     */
    private void handleDocumentPath(GraphConstructedEvent event) {
        TextbookDO doc = textbookRepository.findById(event.getDocumentId())
                .orElseThrow(() -> new IllegalStateException(
                        "文档不存在 documentId=" + event.getDocumentId()));

        // 状态 EXTRACTED → FUSING（同 tx JPA 一级缓存，与 extract() 同一托管实例 · D3）
        doc.setStatus(FileStatus.FUSING);
        textbookRepository.saveAndFlush(doc);

        try {
            fusionService.fuseIncremental(event.getKpNames(), event.getSubject());
            // 融合成功 → COMPLETED
            doc.setStatus(FileStatus.COMPLETED);
            textbookRepository.saveAndFlush(doc);
            log.info("增量融合完成 documentId={}", event.getDocumentId());
        } catch (Exception e) {
            log.error("增量融合失败 documentId={}", event.getDocumentId(), e);
            // 融合失败 → EXTRACTED + failReason（D4 · 修复 latent bug · 用户确认）
            doc.setStatus(FileStatus.EXTRACTED);
            doc.setFailReason("增量融合失败：" + e.getMessage());
            textbookRepository.saveAndFlush(doc);
            // 吞异常，不回滚构建（AC-9）
        }
    }

    /**
     * CSV 路径：仅 fuseFull，无文档状态机。
     *
     * <p>成绩无 document 实体，状态全在 exam_record + Neo4j。
     * 融合失败吞异常（AC-9），fusion_log.status=FAILED 由 FusionServiceImpl 内部记录。</p>
     */
    private void handleCsvPath(GraphConstructedEvent event) {
        try {
            fusionService.fuseFull();
            log.info("全量融合完成 examNo={}", event.getExamNo());
        } catch (Exception e) {
            log.error("全量融合失败 examNo={}", event.getExamNo(), e);
            // 吞异常（AC-9），融合内部已记 fusion_log.status=FAILED
        }
    }
}
