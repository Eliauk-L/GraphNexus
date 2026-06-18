package com.graphnexus.application.graph.construction.service;

import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExtractionJsonParser 单元测试 — 覆盖 LLM 常见畸形 JSON 场景。
 *
 * @author Jay
 * @date 2026/06/16
 */
@DisplayName("ExtractionJsonParser JSON 解析测试")
class ExtractionJsonParserTest {

    private ExtractionJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExtractionJsonParser();
    }

    // ======================== 正常 JSON ========================

    @Test
    @DisplayName("标准合法 JSON 应正常解析")
    void shouldParseValidJson() {
        String json = """
                {
                  "entities": [
                    {"entityType": "DEFINITION", "name": "二次函数", "originalText": "y=ax²+bx+c", "pageNumber": 2}
                  ],
                  "knowledgePoints": [
                    {"name": "二次函数定义", "description": "标准定义", "subject": "数学", "gradeLevel": "初中"}
                  ],
                  "categories": [],
                  "alignments": [],
                  "entityRelations": [],
                  "prerequisites": [],
                  "categoryRelations": []
                }""";

        ExtractionRawResult result = parser.parse(json);

        assertNotNull(result);
        assertEquals(1, result.getEntities().size());
        assertEquals("二次函数", result.getEntities().get(0).getName());
        assertEquals(1, result.getKnowledgePoints().size());
    }

    @Test
    @DisplayName("最小合法 JSON（只有空数组）应正常解析")
    void shouldParseMinimalJson() {
        String json = """
                {"entities":[],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}""";

        ExtractionRawResult result = parser.parse(json);

        assertNotNull(result);
        assertTrue(result.getEntities().isEmpty());
    }

    // ======================== 尾逗号 ========================

    @Test
    @DisplayName("对象尾逗号应被容忍")
    void shouldTolerateTrailingCommaInObject() {
        String json = """
                {"entities":[{"entityType":"DEFINITION","name":"x",}],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[],}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals(1, result.getEntities().size());
    }

    @Test
    @DisplayName("数组尾逗号应被容忍")
    void shouldTolerateTrailingCommaInArray() {
        String json = """
                {"entities":[{"entityType":"DEFINITION","name":"a"},{"entityType":"CONCEPT","name":"b"},],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals(2, result.getEntities().size());
    }

    // ======================== 单引号 ========================

    @Test
    @DisplayName("单引号 JSON 应被容忍")
    void shouldTolerateSingleQuotes() {
        String json = """
                {'entities':[{'entityType':'DEFINITION','name':'test','originalText':'text','pageNumber':1}],'knowledgePoints':[],'categories':[],'alignments':[],'entityRelations':[],'prerequisites':[],'categoryRelations':[]}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals("DEFINITION", result.getEntities().get(0).getEntityType());
    }

    // ======================== 无引号字段名 ========================

    @Test
    @DisplayName("无引号字段名应被容忍")
    void shouldTolerateUnquotedFieldNames() {
        String json = """
                {entities:[{entityType:"DEFINITION",name:"test",originalText:"text",pageNumber:1}],knowledgePoints:[],categories:[],alignments:[],entityRelations:[],prerequisites:[],categoryRelations:[]}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals("test", result.getEntities().get(0).getName());
    }

    // ======================== JS 注释 ========================

    @Test
    @DisplayName("单行注释应被移除")
    void shouldStripSingleLineComments() {
        String json = """
                {"entities":[ // 实体列表
                  {"entityType":"DEFINITION","name":"test","originalText":"text","pageNumber":1}
                ],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals(1, result.getEntities().size());
    }

    @Test
    @DisplayName("多行注释应被移除")
    void shouldStripMultiLineComments() {
        String json = """
                {"entities":[/* 实体开始 */{"entityType":"DEFINITION","name":"test","originalText":"text","pageNumber":1}/* 实体结束 */],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals(1, result.getEntities().size());
    }

    // ======================== Markdown 代码块 ========================

    @Test
    @DisplayName("```json 包裹应被提取")
    void shouldExtractJsonFromMarkdownFence() {
        String json = """
                ```json
                {"entities":[],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}
                ```""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
    }

    @Test
    @DisplayName("``` 包裹（无语言标识）应被提取")
    void shouldExtractPlainMarkdownFence() {
        String json = """
                ```
                {"entities":[],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}
                ```""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
    }

    // ======================== 首尾多余文字 ========================

    @Test
    @DisplayName("JSON 前面的中文说明应被忽略")
    void shouldIgnoreTextBeforeJson() {
        String json = """
                以下是根据文档内容抽取的知识图谱结果：
                {"entities":[],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
    }

    @Test
    @DisplayName("JSON 后面的中文说明应被忽略")
    void shouldIgnoreTextAfterJson() {
        String json = """
                {"entities":[],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}
                以上是完整的抽取结果。""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
    }

    @Test
    @DisplayName("首尾同时有多余文字应正确提取")
    void shouldExtractJsonWithSurroundingText() {
        String json = """
                好的，我已经分析完毕。下面是结果：
                {"entities":[{"entityType":"DEFINITION","name":"x","originalText":"x","pageNumber":1}],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}
                抽取完成，共1个实体。""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals(1, result.getEntities().size());
    }

    // ======================== 组合错误 ========================

    @Test
    @DisplayName("markdown 包裹 + 尾逗号 + 注释组合错误应正常解析")
    void shouldHandleCombinedErrors() {
        String json = """
                结果如下：
                ```json
                {
                  entities: [ // 实体数组
                    {entityType: 'DEFINITION', name: 'test', originalText: 'text', pageNumber: 1,},
                  ],
                  knowledgePoints: [],
                  categories: [],
                  alignments: [],
                  entityRelations: [],
                  prerequisites: [],
                  categoryRelations: [],
                }
                ```
                完成。""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals(1, result.getEntities().size());
        assertEquals("test", result.getEntities().get(0).getName());
    }

    // ======================== 嵌套花括号（括号匹配验证） ========================

    @Test
    @DisplayName("metadata 中包含嵌套 {} 应正确匹配")
    void shouldHandleNestedBracesInMetadata() {
        String json = """
                {"entities":[{"entityType":"DEFINITION","name":"x","originalText":"text","pageNumber":1,"metadata":{"key":"val","nested":{"deep":true}}}],"knowledgePoints":[],"categories":[],"alignments":[],"entityRelations":[],"prerequisites":[],"categoryRelations":[]}""";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
        assertEquals(1, result.getEntities().size());
        assertNotNull(result.getEntities().get(0).getMetadata());
    }

    // ======================== 边界情况 ========================

    @Test
    @DisplayName("null 响应应抛出 BusinessException")
    void shouldThrowOnNullResponse() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(null));
        assertEquals("A0010", ex.getErrorCode());
    }

    @Test
    @DisplayName("空字符串响应应抛出 BusinessException")
    void shouldThrowOnEmptyResponse() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parse(""));
        assertEquals("A0010", ex.getErrorCode());
    }

    @Test
    @DisplayName("无花括号的文本应抛出 BusinessException")
    void shouldThrowOnTextWithoutBraces() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                parser.parse("这是一段没有任何JSON的纯文本回复"));
        assertEquals("A0010", ex.getErrorCode());
    }

    @Test
    @DisplayName("BOM 前缀应被正确处理")
    void shouldHandleBomPrefix() {
        String json = "﻿{\"entities\":[],\"knowledgePoints\":[],\"categories\":[],\"alignments\":[],\"entityRelations\":[],\"prerequisites\":[],\"categoryRelations\":[]}";

        ExtractionRawResult result = parser.parse(json);
        assertNotNull(result);
    }

    // ======================== 预处理单元测试 ========================

    @Nested
    @DisplayName("preprocess 方法")
    class PreprocessTest {

        @Test
        @DisplayName("stripJsComments 应移除单行和多行注释")
        void shouldStripComments() {
            String input = """
                    {
                      /* 多行
                         注释 */
                      "a": 1, // 单行注释
                      "b": 2
                    }""";

            String result = parser.stripJsComments(input);
            assertFalse(result.contains("/*"));
            assertFalse(result.contains("*/"));
            assertFalse(result.contains("//"));
            assertTrue(result.contains("\"a\""));
        }
    }

    @Nested
    @DisplayName("extractJsonObject 方法")
    class ExtractJsonObjectTest {

        @Test
        @DisplayName("应精确提取最外层 JSON 对象")
        void shouldExtractOutermostJson() {
            String input = "前言{\"key\": \"val\"}后记";
            String result = parser.extractJsonObject(input);
            assertEquals("{\"key\": \"val\"}", result);
        }

        @Test
        @DisplayName("嵌套 {} 应被正确匹配")
        void shouldMatchNestedBraces() {
            String input = "{\"outer\": {\"inner\": \"val\"}} trailing";
            String result = parser.extractJsonObject(input);
            assertTrue(result.startsWith("{"));
            assertTrue(result.endsWith("}"));
        }

        @Test
        @DisplayName("无花括号应返回 null")
        void shouldReturnNullForNoBraces() {
            assertNull(parser.extractJsonObject("no braces here"));
        }
    }
}