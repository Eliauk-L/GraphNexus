package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;

import java.util.List;

/**
 * 顶层节点类型抽取处理器 — 可插拔扩展点（D4 / ADR-023）。
 *
 * <p>每个实现代表一类 LLM 可抽取的顶层节点类型，封装其 prompt schema 段、
 * Raw POJO 反序列化、校验、转换四环节。新增顶层节点类型只需实现本接口 + 注册到
 * {@link ExtractionNodeHandlerRegistry}，不修改 ExtractionService 核心分支（AC-4）。</p>
 *
 * @param <R> Raw POJO 类型（Jackson 反序列化目标）
 * @param <N> 领域节点类型（GraphNode 子类）
 * @author Jay
 * @date 2026/06/21
 */
public interface ExtractionNodeHandler<R, N extends GraphNode> {

    /** JSON 段 key（如 "testNodes"），对应 LLM 输出 JSON 的顶层字段名 */
    String sectionKey();

    /** prompt schema 段文本 — 描述本节点类型的 JSON 结构，注入 system prompt 的扩展节点段 */
    String promptSchema();

    /** Raw POJO 的 Class，供 ObjectMapper.convertValue 反序列化扩展段 */
    Class<R> rawType();

    /** 校验本段的 Raw 列表（如必填字段、枚举值），失败抛 BusinessException */
    void validate(List<R> raw, ExtractionRawResult context);

    /** 将 Raw 列表转换为领域节点列表 */
    List<N> convert(List<R> raw, String documentId);
}
