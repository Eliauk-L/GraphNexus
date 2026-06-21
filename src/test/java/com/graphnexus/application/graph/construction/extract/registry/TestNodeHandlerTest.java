package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestNodeHandler 测试 — 对应 AC-4 扩展类型演示载体。
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("TestNodeHandler 扩展节点处理器测试")
class TestNodeHandlerTest {

    private TestNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestNodeHandler();
    }

    @Test
    @DisplayName("sectionKey/rawType/promptSchema 契约正确")
    void contractShouldBeCorrect() {
        assertEquals("testNodes", handler.sectionKey());
        assertEquals(TestNodeRaw.class, handler.rawType());
        assertTrue(handler.promptSchema().contains("testNodes"));
    }

    @Test
    @DisplayName("合法 raw → convert 产出 TestNode 列表")
    void convertShouldProduceTestNodes() {
        TestNodeRaw raw = new TestNodeRaw();
        raw.setName("t1");
        raw.setOriginalText("text");

        List<TestNode> nodes = handler.convert(List.of(raw), "doc-1");

        assertEquals(1, nodes.size());
        assertEquals("t1", nodes.get(0).getName());
        assertEquals("text", nodes.get(0).getOriginalText());
        assertEquals("doc-1", nodes.get(0).getDocumentId());
        assertEquals(TestNode.NODE_TYPE, nodes.get(0).getNodeType());
    }

    @Test
    @DisplayName("name 为空 → validate 抛 BusinessException A0010")
    void validateShouldThrowOnEmptyName() {
        TestNodeRaw raw = new TestNodeRaw();
        raw.setName(null);
        raw.setOriginalText("text");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> handler.validate(List.of(raw), new ExtractionRawResult()));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("testNodes[0]"));
    }

    @Test
    @DisplayName("注册到 registry 后可按 key 查到")
    void registerToRegistryShouldBeFindable() {
        ExtractionNodeHandlerRegistry registry = new ExtractionNodeHandlerRegistry(List.of());
        registry.register(handler);

        assertTrue(registry.findByKey("testNodes").isPresent());
        assertSame(handler, registry.findByKey("testNodes").get());
    }

    @Test
    @DisplayName("空列表 → convert 返回空、validate 不抛")
    void emptyListShouldBeSafe() {
        assertDoesNotThrow(() -> handler.validate(List.of(), new ExtractionRawResult()));
        assertTrue(handler.convert(List.of(), "doc-1").isEmpty());
        assertDoesNotThrow(() -> handler.validate(null, new ExtractionRawResult()));
        assertTrue(handler.convert(null, "doc-1").isEmpty());
    }
}
