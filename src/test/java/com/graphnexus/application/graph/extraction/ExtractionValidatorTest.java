package com.graphnexus.application.graph.extraction;

import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtractionValidator 单元测试（对应 AC-3 JSON Schema 校验）。
 *
 * @author Jay
 * @date 2026/06/13
 */
@DisplayName("ExtractionValidator 校验测试")
class ExtractionValidatorTest {

    private final ExtractionValidator validator = new ExtractionValidator();
    private ExtractionRawResult validResult;

    @BeforeEach
    void setUp() {
        validResult = new ExtractionRawResult();
        // 构建合法实体
        ExtractionRawResult.RawEntity entity = new ExtractionRawResult.RawEntity();
        entity.setEntityType("DEFINITION");
        entity.setName("二次函数定义");
        entity.setOriginalText("二次函数是指形如 y=ax²+bx+c（a≠0）的函数");
        entity.setPageNumber(2);
        validResult.setEntities(List.of(entity));

        // 合法知识点
        ExtractionRawResult.RawKnowledgePoint kp = new ExtractionRawResult.RawKnowledgePoint();
        kp.setName("二次函数定义");
        kp.setDescription("标准定义");
        validResult.setKnowledgePoints(List.of(kp));
    }

    @Test
    @DisplayName("合法 JSON → 校验通过")
    void testValidResult_ShouldPass() {
        assertDoesNotThrow(() -> validator.validate(validResult));
    }

    @Test
    @DisplayName("缺失必填字段 name → BusinessException A0010")
    void testMissingName_ShouldThrow() {
        validResult.getEntities().get(0).setName(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(validResult));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    @DisplayName("entityType 枚举值越界 'UNKNOWN' → BusinessException A0010")
    void testInvalidEntityType_ShouldThrow() {
        validResult.getEntities().get(0).setEntityType("UNKNOWN");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(validResult));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("UNKNOWN"));
    }

    @Test
    @DisplayName("entityRelations 索引越界 → BusinessException A0010")
    void testEntityRelationIndexOutOfBound_ShouldThrow() {
        ExtractionRawResult.RawEntityRelation rel = new ExtractionRawResult.RawEntityRelation();
        rel.setSourceEntityIndex(0);
        rel.setTargetEntityIndex(99); // 越界
        rel.setType("DERIVES");
        validResult.setEntityRelations(List.of(rel));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(validResult));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("越界"));
    }

    @Test
    @DisplayName("entityRelations.type 不在合法枚举值中 → BusinessException A0010")
    void testInvalidRelationType_ShouldThrow() {
        ExtractionRawResult.RawEntityRelation rel = new ExtractionRawResult.RawEntityRelation();
        rel.setSourceEntityIndex(0);
        rel.setTargetEntityIndex(0);
        rel.setType("INVALID_TYPE");
        validResult.setEntities(List.of(
                validResult.getEntities().get(0),
                createEntity("FORMULA", "一般式", "y=ax²+bx+c")
        ));
        validResult.setEntityRelations(List.of(rel));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(validResult));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("INVALID_TYPE"));
    }

    @Test
    @DisplayName("result 为 null → BusinessException A0010")
    void testNullResult_ShouldThrow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(null));
        assertEquals("A0010", ex.getErrorCode());
    }

    @Test
    @DisplayName("entities 数组为空 → BusinessException A0010")
    void testEmptyEntities_ShouldThrow() {
        validResult.setEntities(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> validator.validate(validResult));
        assertEquals("A0010", ex.getErrorCode());
    }

    private ExtractionRawResult.RawEntity createEntity(String type, String name, String text) {
        ExtractionRawResult.RawEntity e = new ExtractionRawResult.RawEntity();
        e.setEntityType(type);
        e.setName(name);
        e.setOriginalText(text);
        return e;
    }
}