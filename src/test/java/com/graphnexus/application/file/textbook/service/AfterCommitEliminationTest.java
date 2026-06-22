package com.graphnexus.application.file.textbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AC-5: afterCommit 手动回调消除验证。
 *
 * <p>扫描 src/main/java 下所有 .java 文件，确认不含
 * {@code TransactionSynchronizationManager.registerSynchronization} 和
 * {@code new TransactionSynchronization}（手动 afterCommit 回调）。</p>
 *
 * @author Jay
 * @date 2026/06/22
 */
@DisplayName("AC-5: afterCommit 消除验证")
class AfterCommitEliminationTest {

    /**
     * AC-5: 生产代码中不再有 TransactionSynchronizationManager.registerSynchronization 调用。
     *
     * <p>搜索关键字符串，确认 afterCommit 模式已从所有代码中移除。</p>
     */
    @Test
    @DisplayName("AC-5: 所有 afterCommit 回调已消除")
    void noAfterCommitCallbacksInProductionCode() throws IOException {
        Path srcDir = Paths.get("src/main/java");
        List<String> violations = new ArrayList<>();

        Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;

                String content = Files.readString(file);
                int lineNum = 0;
                for (String line : content.split("\n")) {
                    lineNum++;
                    if (line.contains("TransactionSynchronizationManager.registerSynchronization")
                            || line.contains("new TransactionSynchronization")) {
                        // 跳过注释行
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                            violations.add(file + ":" + lineNum + ": " + trimmed);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            fail("发现 afterCommit 残留回调（AC-5 失败）:\n" + String.join("\n", violations));
        }
    }
}