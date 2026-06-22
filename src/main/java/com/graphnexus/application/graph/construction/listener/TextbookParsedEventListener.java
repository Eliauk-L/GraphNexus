package com.graphnexus.application.graph.construction.listener;

import com.graphnexus.application.file.textbook.event.TextbookParsedEvent;
import com.graphnexus.application.graph.construction.service.ConstructionService;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 教材解析完成 → 图谱构建监听器。
 *
 * <p>监听 {@link TextbookParsedEvent}，自动触发图谱构建流水线（构建→融合）。
 * 与成绩事件驱动模式一致（{@code GradeUploadedEvent} → {@code GradeGraphEventListener}），
 * 教材/成绩模块不直接依赖图谱模块，仅发布事件。</p>
 *
 * <p>使用 {@code @Async + @EventListener}（替代 AFTER_COMMIT）：
 * 监听器在独立线程中执行，避免 AFTER_COMMIT 回调阶段旧事务 EntityManager
 * 仍绑定在线程上导致 {@code extract()} 无法创建新事务（"no transaction is in progress"）。
 * {@code extract()} 在独立线程中创建全新 JPA 事务，与 {@code parse()} 事务完全隔离。</p>
 *
 * <p>构建失败由本监听器 try-catch 消化，不回滚 PARSED 状态，并将 failReason 写入文档记录，
 * 用户可在前端查看失败原因并手动重试图谱化。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookParsedEventListener {

    private final ConstructionService constructionService;
    private final TextbookRepository textbookRepository;

    @Async("queryAsyncExecutor")
    @EventListener
    public void onTextbookParsed(TextbookParsedEvent event) {
        Long documentId = event.getDocumentId();
        log.info("收到 TextbookParsedEvent，自动触发图谱构建: documentId={}", documentId);
        try {
            constructionService.extract(documentId);
            log.info("图谱构建完成（事件驱动）: documentId={}", documentId);
        } catch (Exception e) {
            log.error("图谱构建失败（事件驱动）: documentId={}, {}", documentId, e.getMessage());
            // extract() 事务已回滚，文档回到 PARSED 状态。
            // 此处单独保存 failReason 到数据库，供前端展示并支持手动重试图谱化。
            saveFailReason(documentId, e.getMessage());
        }
    }

    /**
     * 将抽取失败原因写入文档记录。
     *
     * <p>独立事务方法，确保 failReason 在 extract() 事务回滚后仍能持久化。</p>
     */
    @Transactional
    public void saveFailReason(Long documentId, String errorMessage) {
        textbookRepository.findById(documentId).ifPresent(doc -> {
            doc.setFailReason("LLM抽取失败: " + truncate(errorMessage, 300));
            textbookRepository.save(doc);
            log.info("failReason 已保存: documentId={}", documentId);
        });
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}