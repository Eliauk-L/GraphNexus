package com.graphnexus.infrastructure.mysql.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档状态机单元测试（对应 AC-3）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@DisplayName("DocumentStatus 状态机")
class DocumentStatusTest {

    @Test
    @DisplayName("UPLOADED → PROCESSING 合法")
    void uploadedToProcessingShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.UPLOADED.validateTransition(DocumentStatus.PROCESSING));
    }

    @Test
    @DisplayName("PROCESSING → COMPLETED 合法")
    void processingToCompletedShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.PROCESSING.validateTransition(DocumentStatus.COMPLETED));
    }

    @Test
    @DisplayName("PROCESSING → FAILED 合法")
    void processingToFailedShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.PROCESSING.validateTransition(DocumentStatus.FAILED));
    }

    @Test
    @DisplayName("COMPLETED → PROCESSING 合法（重新解析）")
    void completedToProcessingShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.COMPLETED.validateTransition(DocumentStatus.PROCESSING));
    }

    @Test
    @DisplayName("FAILED → PROCESSING 合法（重试）")
    void failedToProcessingShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.FAILED.validateTransition(DocumentStatus.PROCESSING));
    }

    @Test
    @DisplayName("UPLOADED → COMPLETED 非法（跳过 PROCESSING）")
    void uploadedToCompletedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentStatus.UPLOADED.validateTransition(DocumentStatus.COMPLETED));
    }

    @Test
    @DisplayName("COMPLETED → UPLOADED 非法（禁止回退）")
    void completedToUploadedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentStatus.COMPLETED.validateTransition(DocumentStatus.UPLOADED));
    }

    @Test
    @DisplayName("FAILED → UPLOADED 非法（禁止回退）")
    void failedToUploadedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentStatus.FAILED.validateTransition(DocumentStatus.UPLOADED));
    }

    @Test
    @DisplayName("UPLOADED → FAILED 非法（禁止跳跃）")
    void uploadedToFailedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentStatus.UPLOADED.validateTransition(DocumentStatus.FAILED));
    }

    @Test
    @DisplayName("UPLOADED → DELETING 合法")
    void uploadedToDeletingShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.UPLOADED.validateTransition(DocumentStatus.DELETING));
    }

    @Test
    @DisplayName("COMPLETED → DELETING 合法")
    void completedToDeletingShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.COMPLETED.validateTransition(DocumentStatus.DELETING));
    }

    @Test
    @DisplayName("FAILED → DELETING 合法")
    void failedToDeletingShouldPass() {
        assertDoesNotThrow(() -> DocumentStatus.FAILED.validateTransition(DocumentStatus.DELETING));
    }

    @Test
    @DisplayName("DELETING → 任何状态 非法（终态不可再转换）")
    void deletingCannotTransition() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentStatus.DELETING.validateTransition(DocumentStatus.UPLOADED));
    }

    @Test
    @DisplayName("PROCESSING → DELETING 非法（处理中不可删除）")
    void processingToDeletingShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> DocumentStatus.PROCESSING.validateTransition(DocumentStatus.DELETING));
    }
}