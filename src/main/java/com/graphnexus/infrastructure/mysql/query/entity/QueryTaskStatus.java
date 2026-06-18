package com.graphnexus.infrastructure.mysql.query.entity;

/**
 * 问答任务状态枚举。
 *
 * <p>状态流转：PENDING → PROCESSING → COMPLETED / FAILED。
 * 同步模式下跳过 PENDING，直接在内存中流转完成后写入 COMPLETED。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
public enum QueryTaskStatus {

    /** 任务已提交，等待异步线程拾取 */
    PENDING,

    /** 任务执行中（意图识别 / 剪枝 / LLM 调用） */
    PROCESSING,

    /** 任务成功完成，answer + subgraph_json 已写入 */
    COMPLETED,

    /** 任务失败（LLM 调用失败超过重试次数 / 实体不存在），error_message 已写入 */
    FAILED
}