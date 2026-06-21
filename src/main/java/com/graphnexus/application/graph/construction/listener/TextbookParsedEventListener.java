package com.graphnexus.application.graph.construction.listener;

import com.graphnexus.application.file.textbook.event.TextbookParsedEvent;
import com.graphnexus.application.graph.construction.service.ConstructionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 教材解析完成 → 图谱构建监听器。
 *
 * <p>监听 {@link TextbookParsedEvent}，自动触发图谱构建流水线（构建→融合）。
 * 与成绩事件驱动模式一致（{@code GradeUploadedEvent} → {@code GradeGraphEventListener}），
 * 教材/成绩模块不直接依赖图谱模块，仅发布事件。</p>
 *
 * <p>使用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}：
 * 确保发布方（{@code TextbookServiceImpl.parse()}）的事务先提交 PARSED 状态，
 * 再在新事务中执行抽取。若抽取失败不影响已落库的 PARSED，避免
 * {@code extract()} 异常导致 JPA 事务回滚丢失解析结果。</p>
 *
 * <p>构建失败由本监听器 try-catch 消化，不回滚 PARSED 状态，用户可手动重试抽取。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookParsedEventListener {

    private final ConstructionService constructionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTextbookParsed(TextbookParsedEvent event) {
        Long documentId = event.getDocumentId();
        log.info("收到 TextbookParsedEvent，自动触发图谱构建: documentId={}", documentId);
        try {
            constructionService.extract(documentId);
            log.info("图谱构建完成（事件驱动）: documentId={}", documentId);
        } catch (Exception e) {
            log.error("图谱构建失败（事件驱动）: documentId={}, {}", documentId, e.getMessage());
        }
    }
}