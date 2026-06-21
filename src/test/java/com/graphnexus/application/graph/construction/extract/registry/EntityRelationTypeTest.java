package com.graphnexus.application.graph.construction.extract.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityRelationType 枚举测试 — 对应 AC-3 关系契约源。
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("EntityRelationType 枚举测试")
class EntityRelationTypeTest {

    @Test
    @DisplayName("3 个关系类型均含非空 value + description")
    void allValuesShouldHaveValueAndDescription() {
        assertEquals(3, EntityRelationType.values().length);
        for (EntityRelationType type : EntityRelationType.values()) {
            assertNotNull(type.getValue(), type.name() + " value 为空");
            assertNotNull(type.getDescription(), type.name() + " description 为空");
        }
    }

    @Test
    @DisplayName("fromValue 大小写不敏感 + 未知值返回 null")
    void fromValueShouldBeCaseInsensitive() {
        assertEquals(EntityRelationType.DERIVES, EntityRelationType.fromValue("derives"));
        assertEquals(EntityRelationType.REFERENCES, EntityRelationType.fromValue("REFERENCES"));
        assertNull(EntityRelationType.fromValue("UNKNOWN"));
    }
}
