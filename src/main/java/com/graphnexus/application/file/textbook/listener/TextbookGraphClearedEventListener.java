package com.graphnexus.application.file.textbook.listener;

import com.graphnexus.application.file.textbook.event.TextbookGraphClearedEvent;
import com.graphnexus.infrastructure.mysql.file.entity.TextbookDO;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.file.entity.FileStatus;
import com.graphnexus.infrastructure.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 教材图谱清理完成 → MinIO + MySQL 物理删除监听器。
 *
 * <p>监听 {@link TextbookGraphClearedEvent}（由图谱模块发布），
 * 执行级联删除的最后一步：MinIO 文件清理 → MySQL 物理删除。</p>
 *
 * <p>事件链终结者：TextbookDeletedEvent → N4j清理 → TextbookGraphClearedEvent → 本监听器。</p>
 *
 * <p>使用 {@code @EventListener}（非 {@code @TransactionalEventListener}）：
 * 图谱清理事件可能不在 JPA 事务内发布，使用同步监听器确保级联完整执行。
 * 本方法自行开启事务管理 MinIO + MySQL 操作。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookGraphClearedEventListener {

    private final TextbookRepository textbookRepository;
    private final FileStorageService fileStorageService;

    /**
     * 图谱已清理 → 删除 MinIO 文件 + MySQL 物理删除。
     *
     * <p>MinIO 删除受引用计数保护：仅当无其他非 DELETING 记录引用同一文件路径时才删除。</p>
     */
    @EventListener
    @Transactional
    public void onTextbookGraphCleared(TextbookGraphClearedEvent event) {
        Long documentId = event.getDocumentId();
        String filePath = event.getFilePath();

        log.info("收到 TextbookGraphClearedEvent，执行 MinIO + MySQL 物理删除: documentId={}", documentId);

        // ① 查找文档（幂等：已删除则跳过）
        var docOpt = textbookRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            log.info("文档已不存在（幂等跳过）: id={}", documentId);
            return;
        }
        TextbookDO doc = docOpt.get();

        // ② MinIO 文件删除（引用计数保护）
        String objectKey = fileStorageService.extractObjectKey(filePath);
        long refCount = textbookRepository.countByFilePathAndStatusNot(filePath, FileStatus.DELETING);
        if (refCount <= 1) {
            try {
                fileStorageService.deleteFile(objectKey);
                log.info("MinIO 文件已删除（最后引用）: id={}, filePath={}", documentId, filePath);
            } catch (Exception e) {
                log.warn("MinIO 文件删除失败（可能已被删除，忽略继续）: path={}, error={}",
                        filePath, e.getMessage());
            }
        } else {
            log.info("文件仍被 {} 条其他记录引用，跳过 MinIO 删除: id={}, filePath={}",
                    refCount - 1, documentId, filePath);
        }

        // ③ MySQL 物理删除（图谱清理已成功，执行最终物理删除）
        textbookRepository.delete(doc);
        log.info("文档已物理删除: id={}, filePath={}", documentId, filePath);
    }
}