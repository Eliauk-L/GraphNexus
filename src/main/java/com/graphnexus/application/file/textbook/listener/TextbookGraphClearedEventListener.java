package com.graphnexus.application.file.textbook.listener;

import com.graphnexus.application.file.textbook.event.TextbookGraphClearedEvent;
import com.graphnexus.application.file.textbook.service.TextbookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 教材图谱清理完成 → MinIO + MySQL 物理删除监听器。
 *
 * <p>监听 {@link TextbookGraphClearedEvent}（由图谱模块发布），
 * 委托 {@link TextbookService#finalizeDeletion(Long, String)} 执行
 * 级联删除的最后一步：MinIO 文件清理 → MySQL 物理删除。</p>
 *
 * <p>事件链终结者：TextbookDeletedEvent → N4j清理 → TextbookGraphClearedEvent → 本监听器。</p>
 *
 * <p><b>为何不在本监听器方法上直接用 {@code @Transactional}：</b>
 * 本监听器的 {@code @EventListener} 方法由 {@code TextbookDeletedEventListener}
 * 在 {@code @Async} 线程中通过 {@code eventPublisher.publishEvent()} 同步触发。
 * {@code @EventListener} 适配器可能绕过 AOP 代理直接调用原始 bean，
 * 导致 {@code @Transactional} 不生效。故将事务逻辑抽取到
 * {@code TextbookService#finalizeDeletion()}，通过独立的 Service 代理确保事务正确开启。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookGraphClearedEventListener {

    private final TextbookService textbookService;

    /**
     * 图谱已清理 → 委托 {@link TextbookService#finalizeDeletion} 执行物理删除。
     */
    @EventListener
    public void onTextbookGraphCleared(TextbookGraphClearedEvent event) {
        Long documentId = event.getDocumentId();
        String filePath = event.getFilePath();

        log.info("收到 TextbookGraphClearedEvent，委托 TextbookService.finalizeDeletion: documentId={}", documentId);

        try {
            textbookService.finalizeDeletion(documentId, filePath);
        } catch (Exception e) {
            log.error("物理删除失败: documentId={}, {}", documentId, e.getMessage());
        }
    }
}