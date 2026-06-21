package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtractionNodeHandlerRegistry 测试 — 对应 AC-4 扩展点骨架。
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("ExtractionNodeHandlerRegistry 注册表测试")
class ExtractionNodeHandlerRegistryTest {

    @Test
    @DisplayName("空注入 → all() 为空、findByKey 返回 empty")
    void emptyRegistryShouldReturnEmpty() {
        ExtractionNodeHandlerRegistry registry = new ExtractionNodeHandlerRegistry(List.of());
        assertTrue(registry.all().isEmpty());
        assertTrue(registry.findByKey("any").isEmpty());
    }

    @Test
    @DisplayName("register → all/findByKey 可查到")
    void registerShouldIndexByKey() {
        ExtractionNodeHandlerRegistry registry = new ExtractionNodeHandlerRegistry(List.of());
        StubHandler handler = new StubHandler("testNodes");

        registry.register(handler);

        assertEquals(1, registry.all().size());
        assertTrue(registry.findByKey("testNodes").isPresent());
        assertSame(handler, registry.findByKey("testNodes").get());
    }

    @Test
    @DisplayName("重复 sectionKey → 抛 IllegalStateException")
    void duplicateSectionKeyShouldThrow() {
        ExtractionNodeHandlerRegistry registry = new ExtractionNodeHandlerRegistry(List.of());
        registry.register(new StubHandler("testNodes"));

        assertThrows(IllegalStateException.class,
                () -> registry.register(new StubHandler("testNodes")));
    }

    /** 测试用 stub handler（仅用于注册表行为测试，非真实节点类型） */
    static class StubHandler implements ExtractionNodeHandler<Object, GraphNode> {
        private final String key;

        StubHandler(String key) {
            this.key = key;
        }

        @Override
        public String sectionKey() {
            return key;
        }

        @Override
        public String promptSchema() {
            return "schema-" + key;
        }

        @Override
        public Class<Object> rawType() {
            return Object.class;
        }

        @Override
        public void validate(List<Object> raw, ExtractionRawResult context) {
            // no-op for registry test
        }

        @Override
        public List<GraphNode> convert(List<Object> raw, String documentId) {
            return List.of();
        }
    }
}
