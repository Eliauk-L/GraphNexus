package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TestNode 的 handler — 实现 ExtractionNodeHandler，验证顶层节点类型扩展路径（D4 / AC-4）。
 *
 * <p>普通类（不加 @Component），由测试通过 {@code registry.register()} 手动激活，
 * 避免污染其他 @SpringBootTest。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
public class TestNodeHandler implements ExtractionNodeHandler<TestNodeRaw, TestNode> {

    @Override
    public String sectionKey() {
        return "testNodes";
    }

    @Override
    public String promptSchema() {
        return """
                ## 扩展节点（testNodes，测试用）
                - name：测试节点名称
                - originalText：原文片段
                """;
    }

    @Override
    public Class<TestNodeRaw> rawType() {
        return TestNodeRaw.class;
    }

    @Override
    public void validate(List<TestNodeRaw> raw, ExtractionRawResult context) {
        if (raw == null) {
            return;
        }
        for (int i = 0; i < raw.size(); i++) {
            TestNodeRaw r = raw.get(i);
            if (!StringUtils.hasText(r.getName())) {
                throw new BusinessException(ErrorCode.A0010,
                        String.format("testNodes[%d].name 为空", i));
            }
        }
    }

    @Override
    public List<TestNode> convert(List<TestNodeRaw> raw, String documentId) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(r -> new TestNode(r.getName(), r.getOriginalText(), documentId))
                .collect(Collectors.toList());
    }
}
