package com.graphnexus.infrastructure.mysql.document;

/**
 * 文档生命周期状态。
 *
 * <p>状态流转规则（见 DESIGN § 3.1）：</p>
 * <pre>
 *   UPLOADED  → PROCESSING
 *   PROCESSING → COMPLETED | FAILED
 *   COMPLETED → PROCESSING（重新解析）
 *   FAILED    → PROCESSING（重试）
 * </pre>
 *
 * @author Jay
 * @date 2026/06/12
 */
public enum DocumentStatus {

    /** 已上传，文件在 MinIO，DB 有记录，等待解析 */
    UPLOADED,

    /** 解析进行中（同步场景下为短暂状态） */
    PROCESSING,

    /** 解析成功完成 */
    COMPLETED,

    /** 解析失败 */
    FAILED;

    /**
     * 校验状态转换是否合法。
     *
     * @param target 目标状态
     * @throws IllegalArgumentException 如果转换不合法
     */
    public void validateTransition(DocumentStatus target) {
        if (!getAllowedTargets().contains(target)) {
            throw new IllegalArgumentException(
                    String.format("非法状态转换: %s → %s", this, target)
            );
        }
    }

    /**
     * 获取当前状态允许转换到的目标状态集合。
     */
    private java.util.Set<DocumentStatus> getAllowedTargets() {
        return switch (this) {
            case UPLOADED   -> java.util.Set.of(PROCESSING);
            case PROCESSING -> java.util.Set.of(COMPLETED, FAILED);
            case COMPLETED  -> java.util.Set.of(PROCESSING);
            case FAILED     -> java.util.Set.of(PROCESSING);
        };
    }
}