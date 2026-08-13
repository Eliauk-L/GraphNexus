package com.graphnexus.application.mastery.event;

import com.graphnexus.application.file.grade.event.GradeDeletedEvent;
import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasteryGradeEventListenerOrderTest {

    @Test
    void uploadRunsAfterGraphConstructionAndDeleteRunsBeforeGraphCleanup() throws Exception {
        Order uploadOrder = MasteryGradeEventListener.class
                .getMethod("onGradeUploaded", GradeUploadedEvent.class).getAnnotation(Order.class);
        Order deleteOrder = MasteryGradeEventListener.class
                .getMethod("onGradeDeleted", GradeDeletedEvent.class).getAnnotation(Order.class);

        assertEquals(20, uploadOrder.value());
        assertEquals(5, deleteOrder.value());
    }
}
