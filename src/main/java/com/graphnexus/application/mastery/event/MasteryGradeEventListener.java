package com.graphnexus.application.mastery.event;

import com.graphnexus.application.file.grade.event.GradeDeletedEvent;
import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import com.graphnexus.application.mastery.service.MasteryUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 在成绩图构建后更新掌握度；重复事件通过确定性重放保持幂等。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasteryGradeEventListener {
    private final MasteryUpdateService masteryUpdateService;

    @Order(20) // 上传时先由 GradeGraphEventListener(@Order(10)) 建立 Student/KP 节点
    @EventListener
    public void onGradeUploaded(GradeUploadedEvent event) {
        int count = masteryUpdateService.rebuildForExam(event.getExamNo());
        log.info("考试掌握度更新完成: examNo={}, events={}", event.getExamNo(), count);
    }

    @Order(5) // 删除时先重放 MASTERS，再由 GradeGraphEventListener(@Order(10)) 清理孤点
    @EventListener
    public void onGradeDeleted(GradeDeletedEvent event) {
        int count = masteryUpdateService.rebuildForExam(event.getExamNo());
        log.info("考试删除后掌握度重放完成: examNo={}, events={}", event.getExamNo(), count);
    }
}
