package com.graphnexus.infrastructure.mysql.file.entity;

/**
 * 文档生命周期状态（v2 — 8 状态扩展）。
 *
 * <p>状态流转规则（见 DESIGN extensible-file § 3.2）：</p>
 * <pre>
 *   UPLOADED   → PARSING
 *   PARSING    → PARSED | UPLOADED(failReason) | FAILED(failReason)
 *   PARSED     → EXTRACTING
 *   EXTRACTING → EXTRACTED | PARSED(failReason) | FAILED(failReason)
 *   EXTRACTED  → ALIGNING
 *   ALIGNING   → ALIGNED | EXTRACTED(failReason) | FAILED(failReason)
 *   ALIGNED    → FUSING
 *   FUSING     → COMPLETED | ALIGNED(failReason) | FAILED(failReason)
 *   COMPLETED  → EXTRACTING | ALIGNING | FUSING（手动重新处理）
 *   UPLOADED | PARSED | EXTRACTED | ALIGNED | COMPLETED | FAILED → DELETING
 *   DELETING   → (terminal · 所有组件清除后逻辑删除)
 * </pre>
 *
 * @author Jay
 * @date 2026/06/12
 */
public enum FileStatus {

    /** 已上传，文件在 MinIO，DB 有记录，等待处理 */
    UPLOADED,

    /** 解析进行中（MinerUTextbookParser / PdfBoxTextbookParser / TxtTextbookParser） */
    PARSING,

    /** 解析成功完成，textContent + pageCount 已入库 */
    PARSED,

    /** LLM 知识抽取进行中 */
    EXTRACTING,

    /** 抽取完成，Neo4j 子图已写入 */
    EXTRACTED,

    /** 实体对齐进行中（跨文档 Entity→已有KP 匹配） */
    ALIGNING,

    /** 实体对齐完成 */
    ALIGNED,

    /** 融合进行中（增量 KP 融合 + MASTERS 重算） */
    FUSING,

    /** 全链路成功完成 */
    COMPLETED,

    /** 不可恢复错误 */
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
            case UPLOADED   -> java.util.Set.of(PARSING, DELETING);
            case PARSING    -> java.util.Set.of(PARSED, FAILED, UPLOADED);
            case PARSED     -> java.util.Set.of(EXTRACTING, DELETING);
            case EXTRACTING -> java.util.Set.of(EXTRACTED, FAILED, PARSED);
            case EXTRACTED  -> java.util.Set.of(ALIGNING, DELETING);
            case ALIGNING   -> java.util.Set.of(ALIGNED, FAILED, EXTRACTED);
            case ALIGNED    -> java.util.Set.of(FUSING, DELETING);
            case FUSING     -> java.util.Set.of(COMPLETED, FAILED, ALIGNED);
            case COMPLETED  -> java.util.Set.of(EXTRACTING, ALIGNING, FUSING, DELETING);
            case FAILED     -> java.util.Set.of(PARSING, DELETING);
            case DELETING   -> java.util.Set.of();
        };
    }
}