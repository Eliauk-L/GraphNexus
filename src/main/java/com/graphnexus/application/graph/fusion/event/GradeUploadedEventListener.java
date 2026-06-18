package com.graphnexus.application.graph.fusion.event;

import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.application.graph.metrics.event.GraphChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 成绩上传事件监听器 — 接收 {@link GradeUploadedEvent}，
 * 触发增量知识图谱融合并发布 {@link GraphChangedEvent} 使指标缓存失效。
 *
 * <p>此监听器实现了 file 模块与 graph 模块的事件驱动解耦：
 * file 模块只发布领域事件，不直接依赖 graph 的任何服务。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeUploadedEventListener {

    private final FusionService fusionService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onGradeUploaded(GradeUploadedEvent event) {
        log.info("收到成绩上传事件: examNo={}, subject={}, kpCount={}",
                event.getExamNo(), event.getSubject(), event.getKnowledgePoints().size());

        // 全量融合 — 确保成绩 KPs 与文档 KPs 跨源匹配
        try {
            fusionService.fuseFull();
        } catch (Exception e) {
            log.error("全量融合失败（成绩上传后），examNo={}，可手动重试",
                    event.getExamNo(), e);
        }

        // 图谱变更事件 — 触发指标缓存失效
        eventPublisher.publishEvent(new GraphChangedEvent(this));
    }
}