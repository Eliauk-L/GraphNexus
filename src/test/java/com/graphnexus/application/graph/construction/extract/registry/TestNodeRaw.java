package com.graphnexus.application.graph.construction.extract.registry;

import lombok.Data;

/**
 * TestNode 的 Raw POJO — LLM 输出 JSON 中 testNodes 段的反序列化目标（D4 / AC-4）。
 *
 * @author Jay
 * @date 2026/06/21
 */
@Data
public class TestNodeRaw {
    private String name;
    private String originalText;
}
