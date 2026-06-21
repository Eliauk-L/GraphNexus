package com.graphnexus.application.analysis.fusion.service;

import com.graphnexus.application.analysis.fusion.model.FusionExecuteResult;
import com.graphnexus.application.analysis.fusion.model.FusionRollbackResult;
import com.graphnexus.application.analysis.fusion.model.FusionStatusResult;

import java.util.List;

/**
 * 宽图谱融合服务接口 — KP 融合 + MASTERS 聚合 + 回滚。
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface FusionService {

    /**
     * 手动全量融合：合并所有 subject 的 KP + 全量重算 MASTERS。
     */
    FusionExecuteResult fuseFull();

    /**
     * 增量融合：仅合并指定 KP 名称范围内的 KP + 重算受影响学生的 MASTERS。
     *
     * @param kpNames 受影响的 KP 名称列表
     * @param subject 学科
     */
    FusionExecuteResult fuseIncremental(List<String> kpNames, String subject);

    /**
     * 基于融合日志回滚指定融合操作。
     *
     * @param fusionLogId 融合日志 ID
     */
    FusionRollbackResult rollback(Long fusionLogId);

    /**
     * 查询最近一次融合的状态。
     */
    FusionStatusResult getStatus();
}