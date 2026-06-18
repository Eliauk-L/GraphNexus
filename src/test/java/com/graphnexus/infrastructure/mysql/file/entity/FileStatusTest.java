package com.graphnexus.infrastructure.mysql.file.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档状态机单元测试（对应 AC-3）。
 *
 * @author Jay
 * @date 2026/06/12
 */
@DisplayName("FileStatus 状态机")
class FileStatusTest {

    @Test
    @DisplayName("UPLOADED → PROCESSING 合法")
    void uploadedToProcessingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.UPLOADED.validateTransition(FileStatus.PROCESSING));
    }

    @Test
    @DisplayName("PROCESSING → COMPLETED 合法")
    void processingToCompletedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.PROCESSING.validateTransition(FileStatus.COMPLETED));
    }

    @Test
    @DisplayName("PROCESSING → FAILED 合法")
    void processingToFailedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.PROCESSING.validateTransition(FileStatus.FAILED));
    }

    @Test
    @DisplayName("COMPLETED → PROCESSING 合法（重新解析）")
    void completedToProcessingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.COMPLETED.validateTransition(FileStatus.PROCESSING));
    }

    @Test
    @DisplayName("FAILED → PROCESSING 合法（重试）")
    void failedToProcessingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.FAILED.validateTransition(FileStatus.PROCESSING));
    }

    @Test
    @DisplayName("UPLOADED → COMPLETED 非法（跳过 PROCESSING）")
    void uploadedToCompletedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> FileStatus.UPLOADED.validateTransition(FileStatus.COMPLETED));
    }

    @Test
    @DisplayName("COMPLETED → UPLOADED 非法（禁止回退）")
    void completedToUploadedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> FileStatus.COMPLETED.validateTransition(FileStatus.UPLOADED));
    }

    @Test
    @DisplayName("FAILED → UPLOADED 非法（禁止回退）")
    void failedToUploadedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> FileStatus.FAILED.validateTransition(FileStatus.UPLOADED));
    }

    @Test
    @DisplayName("UPLOADED → FAILED 非法（禁止跳跃）")
    void uploadedToFailedShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> FileStatus.UPLOADED.validateTransition(FileStatus.FAILED));
    }

    @Test
    @DisplayName("UPLOADED → DELETING 合法")
    void uploadedToDeletingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.UPLOADED.validateTransition(FileStatus.DELETING));
    }

    @Test
    @DisplayName("COMPLETED → DELETING 合法")
    void completedToDeletingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.COMPLETED.validateTransition(FileStatus.DELETING));
    }

    @Test
    @DisplayName("FAILED → DELETING 合法")
    void failedToDeletingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.FAILED.validateTransition(FileStatus.DELETING));
    }

    @Test
    @DisplayName("DELETING → 任何状态 非法（终态不可再转换）")
    void deletingCannotTransition() {
        assertThrows(IllegalArgumentException.class,
                () -> FileStatus.DELETING.validateTransition(FileStatus.UPLOADED));
    }

    @Test
    @DisplayName("PROCESSING → DELETING 非法（处理中不可删除）")
    void processingToDeletingShouldFail() {
        assertThrows(IllegalArgumentException.class,
                () -> FileStatus.PROCESSING.validateTransition(FileStatus.DELETING));
    }
}