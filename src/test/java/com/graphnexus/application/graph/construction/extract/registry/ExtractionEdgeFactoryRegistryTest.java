package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.infrastructure.neo4j.edge.ContainsEdge;
import com.graphnexus.infrastructure.neo4j.edge.DerivesEdge;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.edge.ReferencesEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtractionEdgeFactoryRegistry 测试 — 对应 AC-3（关系类型可插拔，无 switch）。
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("ExtractionEdgeFactoryRegistry 关系工厂注册表测试")
class ExtractionEdgeFactoryRegistryTest {

    private ExtractionEdgeFactoryRegistry registry() {
        return new ExtractionEdgeFactoryRegistry(List.of(
                new DerivesEdgeFactory(), new ContainsEdgeFactory(), new ReferencesEdgeFactory()));
    }

    @Test
    @DisplayName("DERIVES → DerivesEdge")
    void createsDerivesEdge() {
        GraphEdge edge = registry().create(EntityRelationType.DERIVES, "s", "t", "d");
        assertInstanceOf(DerivesEdge.class, edge);
        assertEquals("DERIVES", edge.getEdgeType());
    }

    @Test
    @DisplayName("CONTAINS → ContainsEdge")
    void createsContainsEdge() {
        GraphEdge edge = registry().create(EntityRelationType.CONTAINS, "s", "t", "d");
        assertInstanceOf(ContainsEdge.class, edge);
    }

    @Test
    @DisplayName("REFERENCES → ReferencesEdge")
    void createsReferencesEdge() {
        GraphEdge edge = registry().create(EntityRelationType.REFERENCES, "s", "t", "d");
        assertInstanceOf(ReferencesEdge.class, edge);
    }

    @Test
    @DisplayName("所有关系类型均注册（数据驱动遍历 · AC-3 无 switch）")
    void allRelationTypesRegistered() {
        ExtractionEdgeFactoryRegistry reg = registry();
        for (EntityRelationType type : EntityRelationType.values()) {
            GraphEdge edge = reg.create(type, "s", "t", "d");
            assertNotNull(edge);
            assertEquals(type.getValue(), edge.getEdgeType());
        }
    }

    @Test
    @DisplayName("重复 relationType 注册 → 抛 IllegalStateException")
    void duplicateRelationTypeShouldThrow() {
        assertThrows(IllegalStateException.class, () ->
                new ExtractionEdgeFactoryRegistry(List.of(
                        new DerivesEdgeFactory(), new DerivesEdgeFactory())));
    }
}
