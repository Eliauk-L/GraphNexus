package com.graphnexus.api.graph.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图指标查询请求参数 —— Controller 层接收 {@code @RequestParam} 绑定。
 *
 * <p>空列表表示全图默认（所有节点类型 + 所有边类型）。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Data
public class MetricsQueryRequest {

    /** 节点类型标签列表（逗号分隔），如 KnowledgePoint,Entity。空 = 全类型 */
    private List<String> nodeTypes = Collections.emptyList();

    /** 边类型列表（逗号分隔），如 PREREQUISITE_OF,ALIGNED_TO。空 = 全类型 */
    private List<String> edgeTypes = Collections.emptyList();

    /**
     * 去重的 Set（空列表 → 空 Set，保留原始大小写，由 MetricsServiceImpl 归一化为规范标签）。
     */
    public Set<String> nodeTypeSet() {
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            return Collections.emptySet();
        }
        return nodeTypes.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    /**
     * 去重的 Set（空列表 → 空 Set，保留原始大小写，由 MetricsServiceImpl 归一化为规范标签）。
     */
    public Set<String> edgeTypeSet() {
        if (edgeTypes == null || edgeTypes.isEmpty()) {
            return Collections.emptySet();
        }
        return edgeTypes.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
    }
}