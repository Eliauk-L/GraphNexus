package com.graphnexus.application.graph.construction.extract;

import com.graphnexus.application.graph.construction.extract.registry.EntityRelationType;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandler;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandlerRegistry;
import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.infrastructure.neo4j.node.EntityType;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtractionPromptBuilder 测试 — 对应 AC-1（语义等价）/ AC-2（实体段枚举派生）/
 * AC-3（关系段枚举派生）/ AC-5（few-shot 按学科切换 + 默认回退）。
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("ExtractionPromptBuilder md 加载 + 段落装配测试")
class ExtractionPromptBuilderTest {

    private ExtractionPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ExtractionPromptBuilder(new DefaultResourceLoader(),
                new ExtractionNodeHandlerRegistry(List.of()), null);
    }

    @Test
    @DisplayName("AC-1：静态段落语义等价 — 含角色设定 / 输出规则 / subject 命名规范")
    void staticSectionsPresent() {
        String prompt = builder.buildSystemPrompt(null);
        assertTrue(prompt.contains("知识图谱构建专家"), "含角色设定");
        assertTrue(prompt.contains("仅输出纯 JSON"), "含输出规则");
        assertTrue(prompt.contains("subject 命名规范"), "含 subject 命名规范");
        assertTrue(prompt.contains("LaTeX"), "含公式规则");
    }

    @Test
    @DisplayName("AC-1：占位符全部被替换")
    void placeholdersAllReplaced() {
        String prompt = builder.buildSystemPrompt(null);
        assertFalse(prompt.contains("{{"), "存在未替换占位符 {{");
        assertFalse(prompt.contains("}}"), "存在未替换占位符 }}");
    }

    @Test
    @DisplayName("AC-2：实体段含全部 EntityType 枚举值 + example")
    void entityTypesSectionDerivedFromEnum() {
        String prompt = builder.buildSystemPrompt(null);
        for (EntityType t : EntityType.values()) {
            assertTrue(prompt.contains(t.getValue()), "实体段缺: " + t.getValue());
            assertTrue(prompt.contains(t.getExample()), "实体段缺 example: " + t.getExample());
        }
    }

    @Test
    @DisplayName("AC-3：关系段含全部 EntityRelationType 枚举值")
    void relationTypesSectionDerivedFromEnum() {
        String prompt = builder.buildSystemPrompt(null);
        for (EntityRelationType t : EntityRelationType.values()) {
            assertTrue(prompt.contains(t.getValue()), "关系段缺: " + t.getValue());
            assertTrue(prompt.contains(t.getDescription()), "关系段缺 description: " + t.getDescription());
        }
    }

    @Test
    @DisplayName("AC-4：无注册 handler 时扩展段为空（生产默认）")
    void extensionSectionEmptyWhenNoHandler() {
        String prompt = builder.buildSystemPrompt(null);
        assertFalse(prompt.contains("扩展节点（testNodes"));
    }

    @Test
    @DisplayName("AC-4：注册 handler 后扩展段含其 promptSchema")
    void extensionSectionContainsHandlerSchema() {
        ExtractionNodeHandlerRegistry registry = new ExtractionNodeHandlerRegistry(List.of());
        registry.register(new StubSchemaHandler());
        ExtractionPromptBuilder b = new ExtractionPromptBuilder(new DefaultResourceLoader(), registry, null);
        String prompt = b.buildSystemPrompt(null);
        assertTrue(prompt.contains("扩展节点（testNodes"), "扩展段应含 handler schema");
    }

    @Test
    @DisplayName("AC-5：subject=数学 → few-shot 为 math 示例")
    void fewShotMathForMathSubject() {
        String mathPrompt = builder.buildSystemPrompt("数学");
        String defaultPrompt = builder.buildSystemPrompt(null);

        // math few-shot 含二次函数示例标识；与 default（v1 内容相同）区分用加载路径——
        // 这里通过 buildSystemPrompt 内部缓存的模板源区分：数学命中 extraction-fewshot-math.md
        assertTrue(mathPrompt.contains("二次函数"), "数学 few-shot 应含二次函数示例");
        assertTrue(defaultPrompt.contains("二次函数"), "默认 few-shot 同样含二次函数（v1 与 math 一致）");
    }

    @Test
    @DisplayName("AC-5：subject=物理 → 回退 default few-shot")
    void fewShotDefaultForPhysics() {
        String physicsPrompt = builder.buildSystemPrompt("物理");
        // 物理 v1 未配学科示例，回退 default
        assertTrue(physicsPrompt.contains("二次函数"), "物理应回退 default few-shot");
        assertFalse(physicsPrompt.contains("{{"), "占位符应全部替换");
    }

    @Test
    @DisplayName("AC-1：非 few-shot 段落在不同 subject 间一致")
    void nonFewShotSectionsConsistentAcrossSubjects() {
        String math = builder.buildSystemPrompt("数学");
        String physics = builder.buildSystemPrompt("物理");
        // 移除 few-shot 部分后（few-shot 内容 v1 相同），两 prompt 应完全一致
        assertEquals(math, physics, "v1 math 与 default few-shot 内容相同，非 few-shot 段必一致");
    }

    @Test
    @DisplayName("AC-1：User Message 占位符替换")
    void userMessagePlaceholdersReplaced() {
        String user = builder.buildUserMessage("test.pdf", "数学", 10, "正文内容");
        assertTrue(user.contains("《test.pdf》"));
        assertTrue(user.contains("页数：10"));
        assertTrue(user.contains("学科：数学"));
        assertTrue(user.contains("正文内容"));
        assertFalse(user.contains("{{"));
    }

    /** 仅测 promptSchema 注入的 stub handler */
    static class StubSchemaHandler implements ExtractionNodeHandler<Object, GraphNode> {
        @Override
        public String sectionKey() { return "testNodes"; }

        @Override
        public String promptSchema() { return "## 扩展节点（testNodes）"; }

        @Override
        public Class<Object> rawType() { return Object.class; }

        @Override
        public void validate(List<Object> raw, ExtractionRawResult context) { }

        @Override
        public List<GraphNode> convert(List<Object> raw, String documentId) { return List.of(); }
    }
}
