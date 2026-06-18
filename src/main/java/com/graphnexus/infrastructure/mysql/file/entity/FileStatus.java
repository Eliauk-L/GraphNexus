package com.graphnexus.infrastructure.mysql.file.entity;

/**
 * 文档生命周期状态。
 *
 * <p>状态流转规则（见 DESIGN § 3.1）：</p>
 * <pre>
 *   UPLOADED  → PROCESSING
 *   PROCESSING → COMPLETED | FAILED
 *   COMPLETED → PROCESSING（重新解析）
 *   FAILED    → PROCESSING（重试）
 *   UPLOADED | COMPLETED | FAILED → DELETING（请求删除）
 *   DELETING  → 所有组件清除后逻辑删除（is_deleted = 1）
 * </pre>
 *
 * @author Jay
 * @date 2026/06/12
 */
public enum FileStatus {

    /** 已上传，文件在 MinIO，DB 有记录，等待解析 */
    UPLOADED,

    /** 解析进行中（同步场景下为短暂状态） */
    PROCESSING,

    /** 解析成功完成 */
    COMPLETED,

    /** 解析失败 */
    FAILED,

    /** 删除进行中 — 中间状态，等待 MinIO + Neo4j 清理完成后转为逻辑删除 */
    DELETING;

    /**
     * 校验状态转换是否合法。
     *
     * @param target 目标状态
     * @throws IllegalArgumentException 如果转换不合法
     */
    public void validateTransition(FileStatus target) {
        if (!getAllowedTargets().contains(target)) {
            throw new IllegalArgumentException(
                    String.format("非法状态转换: %s → %s", this, target)
            );
        }
    }

    /**
     * 获取当前状态允许转换到的目标状态集合。
     */
    private java.util.Set<FileStatus> getAllowedTargets() {
        return switch (this) {
            case UPLOADED   -> java.util.Set.of(PROCESSING, DELETING);
            case PROCESSING -> java.util.Set.of(COMPLETED, FAILED);
            case COMPLETED  -> java.util.Set.of(PROCESSING, DELETING);
            case FAILED     -> java.util.Set.of(PROCESSING, DELETING);
            case DELETING   -> java.util.Set.of(); // 终态：进入后不可再转换，由 markDeleted 完成
        };
    }
}