/**
 * 图剪枝策略接口 — 从宽图谱中按任务类型裁剪最小够用子图。
 *
 * <p>所有剪枝策略实现此接口。v1 首发 {@code StudentDiagnosisStrategy}。
 * 新增意图只需实现此接口 + 在策略调度处注册即可。</p>
 *
 * @author Jay
 * @date 2026/06/17
 * @see com.graphnexus.application.analysis.model.PruningRequest
 * @see com.graphnexus.application.analysis.model.PrunedSubgraph
 */
package com.graphnexus.application.analysis.strategy;

import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.application.analysis.model.PrunedSubgraph;

/**
 * 图剪枝策略契约 — 输入剪枝请求，输出最小够用子图。
 *
 * <p>实现类职责：</p>
 * <ul>
 *   <li>根据 {@link PruningRequest#intent()} 执行对应的 Cypher 剪枝逻辑</li>
 *   <li>若 MASTERS 边不可用（融合未执行），自动降级为 TESTED 路径查询</li>
 *   <li>在 {@link PrunedSubgraph.PruningMeta} 中标记降级状态</li>
 * </ul>
 *
 * @see PruningRequest
 * @see PrunedSubgraph
 */
@FunctionalInterface
public interface SubgraphPruningStrategy {

    /**
     * 执行图剪枝，从宽图谱中裁剪出任务驱动的最小子图。
     *
     * @param request 剪枝请求（意图类型 + 目标实体 + 学科 + 扩展参数）
     * @return 剪枝后的子图（节点 + 边 + 元信息）
     */
    PrunedSubgraph prune(PruningRequest request);
}