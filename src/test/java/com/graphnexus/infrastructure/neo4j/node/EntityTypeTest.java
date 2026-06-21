package com.graphnexus.infrastructure.neo4j.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EntityType 枚举测试 — 对应 AC-2 实体分类单一来源（example 字段供 prompt 段派生）。
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("EntityType 枚举测试")
class EntityTypeTest {

    @Test
    @DisplayName("5 个枚举值均含非空 example 字段")
    void allValuesShouldHaveExample() {
        for (EntityType type : EntityType.values()) {
            assertNotNull(type.getExample(), type.name() + " 的 example 为空");
            assertFalse(type.getExample().isBlank(), type.name() + " 的 example 为空白");
        }
    }

    @Test
    @DisplayName("fromValue 大小写不敏感查找 + 未知值返回 null")
    void fromValueShouldBeCaseInsensitive() {
        assertEquals(EntityType.DEFINITION, EntityType.fromValue("definition"));
        assertEquals(EntityType.FORMULA, EntityType.fromValue("FORMULA"));
        assertNull(EntityType.fromValue("UNKNOWN"));
    }
}
