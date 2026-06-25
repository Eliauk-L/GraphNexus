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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 图谱构建完成事件监听器 —— 同步消费 {@link GraphConstructedEvent}，触发下游融合。
 *
 * <p>消费方归 analysis 模块（ADR-024 依赖方向：construction(graph) → 事件 → fusion(analysis)）。
 * 使用 plain {@link EventListener}（ADR-028 规则 5：不标注 @Transactional）。</p>
 *
 * <p>按 {@code source} 分支处理：</p>
 * <ul>
 *   <li><b>DOCUMENT</b>（mode=INCREMENTAL）：文档状态机 FUSING→{COMPLETED, EXTRACTED+failReason}（D4）</li>
 *   <li><b>CSV</b>（mode=FULL）：仅 fuseFull，无文档状态机</li>
 * </ul>
 *
 * <p><b>事务策略（ADR-028）</b>：状态变更使用独立短事务（规则 1），
 * 不依赖发布方事务上下文。融合失败通过短事务回退状态 + failReason（ADR-029 补偿）。</p>
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
    private final PlatformTransactionManager txManager;

    /**
     * 消费图谱构建完成事件，触发融合。
     *
     * <p>同步执行（同线程），发布方（extract）已不含 @Transactional，
     * 状态变更使用独立短事务（ADR-028 规则 1+5）。</p>
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
     * <p>每步状态变更使用独立短事务（ADR-028 规则 1）。融合失败时回退到 EXTRACTED + failReason（ADR-029）。</p>
     */
    private void handleDocumentPath(GraphConstructedEvent event) {
        Long documentId = event.getDocumentId();
        TransactionTemplate jpaTx = new TransactionTemplate(txManager);

        // ===== 短事务：EXTRACTED → FUSING（ADR-028 规则 1）=====
        jpaTx.executeWithoutResult(status -> {
            TextbookDO doc = textbookRepository.findById(documentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "文档不存在 documentId=" + documentId));
            doc.setStatus(FileStatus.FUSING);
            textbookRepository.save(doc);
            log.info("状态→FUSING: documentId={}", documentId);
        });
        // FUSING 已提交 → 前端轮询可见

        // ===== 融合操作（Neo4j 事务 · FusionService 自管理）=====
        try {
            fusionService.fuseIncremental(event.getKpNames(), event.getSubject());

            // ===== 短事务：融合成功 → COMPLETED =====
            jpaTx.executeWithoutResult(status -> {
                textbookRepository.findById(documentId).ifPresent(doc -> {
                    doc.setStatus(FileStatus.COMPLETED);
                    textbookRepository.save(doc);
                    log.info("增量融合完成 documentId={}, status→COMPLETED", documentId);
                });
            });
        } catch (Exception e) {
            log.error("增量融合失败 documentId={}", documentId, e);

            // ===== 短事务：融合失败 → EXTRACTED + failReason（ADR-029 补偿）=====
            jpaTx.executeWithoutResult(status -> {
                textbookRepository.findById(documentId).ifPresent(doc -> {
                    doc.setStatus(FileStatus.EXTRACTED);
                    doc.setFailReason("增量融合失败：" + truncate(e.getMessage(), 300));
                    textbookRepository.save(doc);
                    log.info("融合失败，状态回退 EXTRACTED: documentId={}", documentId);
                });
            });
            // 吞异常，不回滚构建（AC-9）
        }
    }

    /**
     * CSV 路径：仅 fuseFull，无文档状态机。
     */
    private void handleCsvPath(GraphConstructedEvent event) {
        try {
            fusionService.fuseFull();
            log.info("全量融合完成 examNo={}", event.getExamNo());
        } catch (Exception e) {
            log.error("全量融合失败 examNo={}", event.getExamNo(), e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}