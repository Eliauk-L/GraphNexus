package com.graphnexus.application.graph.construction.listener;

import com.graphnexus.application.file.textbook.event.TextbookParsedEvent;
import com.graphnexus.application.graph.construction.service.ConstructionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 教材解析完成 → 图谱构建监听器。
 *
 * <p>监听 {@link TextbookParsedEvent}，自动触发图谱构建流水线（构建→融合）。
 * 与成绩事件驱动模式一致（{@code GradeUploadedEvent} → {@code GradeGraphEventListener}），
 * 教材/成绩模块不直接依赖图谱模块，仅发布事件。</p>
 *
 * <p>构建失败不抛异常（由 {@code ConstructionService} 内部 try-catch 消化），
 * 确保解析请求不受影响。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextbookParsedEventListener {

    private final ConstructionService constructionService;

    @EventListener
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