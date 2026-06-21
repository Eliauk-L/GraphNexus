package com.graphnexus.application.graph.construction.listener;

import com.graphnexus.application.file.textbook.event.TextbookDeletedEvent;
import com.graphnexus.application.file.textbook.event.TextbookGraphClearedEvent;
import com.graphnexus.common.event.GraphChangedEvent;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 教材删除 → Neo4j 图谱清理监听器。
 *
 * <p>监听 {@link TextbookDeletedEvent}，清理 Neo4j 中对应文档的子图和节点，
 * 清理成功后发布 {@link TextbookGraphClearedEvent} 触发后续 MinIO + MySQL 物理删除。</p>
 *
 * <p>使用 {@code @Async + @EventListener}（而非 {@code @TransactionalEventListener}）：
 * 与 {@code TextbookParsedEventListener} 保持一致，在独立线程中执行，
 * 避免 AFTER_COMMIT 回调阶段旧事务 EntityManager 仍绑定在线程上导致下游
 * {@code TextbookGraphClearedEventListener}（{@code @Transactional} JPA）无法创建新事务。</p>
 *
 * <p>发布方（{@code TextbookServiceImpl.deleteTextBook()}）已使用
 * {@code TransactionSynchronizationManager.registerSynchronization().afterCommit()}
 * 确保 DELETING 状态落库后才发布事件，@Async 线程读到的一定是已提交状态。</p>
 *
 * <p>事件链：TextbookDeletedEvent → 本监听器(N4j清理) → TextbookGraphClearedEvent
 * → TextbookGraphClearedEventListener(MinIO+MySQL)</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookDeletedEventListener {

    private final ConstructionGraphRepository constructionGraphRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Async("queryAsyncExecutor")
    @EventListener
    public void onTextbookDeleted(TextbookDeletedEvent event) {
        Long documentId = event.getDocumentId();
        log.info("收到 TextbookDeletedEvent，开始清理 Neo4j 图谱: documentId={}", documentId);

        try {
            constructionGraphRepository.deleteByDocumentId(String.valueOf(documentId));
            log.info("Neo4j 图谱清理完成: documentId={}", documentId);
        } catch (Exception e) {
            log.error("Neo4j 图谱清理失败: documentId={}, {}", documentId, e.getMessage());
            // 图谱清理失败不继续级联，保留 DELETING 状态等待重试
            return;
        }

        // 图谱结构已变更，触发指标缓存失效
        eventPublisher.publishEvent(new GraphChangedEvent(this));

        // 发布图谱清理完成事件 → 教材模块执行 MinIO + MySQL 物理删除
        eventPublisher.publishEvent(new TextbookGraphClearedEvent(
                this, documentId, event.getFilePath(), event.getDocumentNo()));
        log.info("已发布 TextbookGraphClearedEvent: documentId={}", documentId);
    }
}