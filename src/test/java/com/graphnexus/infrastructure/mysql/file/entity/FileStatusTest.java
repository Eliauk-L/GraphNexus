package com.graphnexus.infrastructure.mysql.file.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档状态机 v2 单元测试 — 8 状态模型。
 *
 * @author Jay
 * @date 2026/06/18
 */
@DisplayName("FileStatus v2 状态机")
class FileStatusTest {

    // ==================== 正向路径 ====================

    @Test
    @DisplayName("UPLOADED → PARSING 合法")
    void uploadedToParsingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.UPLOADED.validateTransition(FileStatus.PARSING));
    }

    @Test
    @DisplayName("PARSING → PARSED 合法")
    void parsingToParsedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.PARSING.validateTransition(FileStatus.PARSED));
    }

    @Test
    @DisplayName("PARSED → EXTRACTING 合法")
    void parsedToExtractingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.PARSED.validateTransition(FileStatus.EXTRACTING));
    }

    @Test
    @DisplayName("EXTRACTING → EXTRACTED 合法")
    void extractingToExtractedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.EXTRACTING.validateTransition(FileStatus.EXTRACTED));
    }

    @Test
    @DisplayName("EXTRACTED → FUSING 合法（跨文档对齐由融合隐式完成）")
    void extractedToFusingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.EXTRACTED.validateTransition(FileStatus.FUSING));
    }

    @Test
    @DisplayName("FUSING → COMPLETED 合法")
    void fusingToCompletedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.FUSING.validateTransition(FileStatus.COMPLETED));
    }

    // ==================== 失败回退 ====================

    @Test
    @DisplayName("PARSING → UPLOADED 合法（可恢复失败回退）")
    void parsingToUploadedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.PARSING.validateTransition(FileStatus.UPLOADED));
    }

    @Test
    @DisplayName("PARSING → FAILED 合法（不可恢复失败）")
    void parsingToFailedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.PARSING.validateTransition(FileStatus.FAILED));
    }

    @Test
    @DisplayName("EXTRACTING → PARSED 合法（可恢复失败回退）")
    void extractingToParsedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.EXTRACTING.validateTransition(FileStatus.PARSED));
    }

    @Test
    @DisplayName("FUSING → EXTRACTED 合法（可恢复失败回退）")
    void fusingToExtractedShouldPass() {
        assertDoesNotThrow(() -> FileStatus.FUSING.validateTransition(FileStatus.EXTRACTED));
    }

    // ==================== 手动重新处理 ====================

    @Test
    @DisplayName("COMPLETED → PARSING 合法（重新解析）")
    void completedToParsingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.COMPLETED.validateTransition(FileStatus.PARSING));
    }

    @Test
    @DisplayName("COMPLETED → FUSING 合法（重新融合）")
    void completedToFusingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.COMPLETED.validateTransition(FileStatus.FUSING));
    }

    @Test
    @DisplayName("FAILED → PARSING 合法（从头重试）")
    void failedToParsingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.FAILED.validateTransition(FileStatus.PARSING));
    }

    // ==================== 删除路径 ====================

    @Test
    @DisplayName("UPLOADED → DELETING 合法")
    void uploadedToDeletingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.UPLOADED.validateTransition(FileStatus.DELETING));
    }

    @Test
    @DisplayName("PARSED → DELETING 合法")
    void parsedToDeletingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.PARSED.validateTransition(FileStatus.DELETING));
    }

    @Test
    @DisplayName("EXTRACTED → DELETING 合法")
    void extractedToDeletingShouldPass() {
        assertDoesNotThrow(() -> FileStatus.EXTRACTED.validateTransition(FileStatus.DELETING));
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

    // ==================== 非法跳转 ====================

    @Nested
    @DisplayName("非法跳转")
    class IllegalTransitions {

        @Test
        @DisplayName("UPLOADED → COMPLETED 非法（跳过所有步骤）")
        void uploadedToCompletedShouldFail() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.UPLOADED.validateTransition(FileStatus.COMPLETED));
        }

        @Test
        @DisplayName("UPLOADED → PARSED 非法（跳跃）")
        void uploadedToParsedShouldFail() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.UPLOADED.validateTransition(FileStatus.PARSED));
        }

        @Test
        @DisplayName("COMPLETED → UPLOADED 非法（禁止回退）")
        void completedToUploadedShouldFail() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.COMPLETED.validateTransition(FileStatus.UPLOADED));
        }

        @Test
        @DisplayName("PARSED → UPLOADED 非法（禁止回退到上上游）")
        void parsedToUploadedShouldFail() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.PARSED.validateTransition(FileStatus.UPLOADED));
        }

        @Test
        @DisplayName("DELETING → 任何状态 非法（终态）")
        void deletingCannotTransition() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.DELETING.validateTransition(FileStatus.UPLOADED));
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.DELETING.validateTransition(FileStatus.COMPLETED));
        }

        @Test
        @DisplayName("*ING 状态不可直接删除（必须回退到 *ED 才能删）")
        void ingStatesCannotDelete() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.PARSING.validateTransition(FileStatus.DELETING));
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.EXTRACTING.validateTransition(FileStatus.DELETING));
            assertThrows(IllegalArgumentException.class,
                    () -> FileStatus.FUSING.validateTransition(FileStatus.DELETING));
        }
    }
}