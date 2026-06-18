package com.graphnexus.application.query.prompt.service;

import com.graphnexus.application.query.chat.model.QueryIntent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptTemplateService 字符串替换逻辑单元测试（不依赖 Spring Context）。
 *
 * @author Jay
 * @date 2026/06/17
 */
class PromptTemplateServiceTest {

    /**
     * 裸 PromptTemplateService 用于测试字符串操作，不需要 ResourceLoader。
     */
    private static class TestablePromptTemplateService extends PromptTemplateService {
        TestablePromptTemplateService() {
            super(null); // ResourceLoader not needed for assemble/withMastersDegradation
        }

        @Override
        String loadTemplate(String name) {
            // 返回简单测试模板
            return "## 分析报告\n\n学生: {{studentName}}\n{{mastersWarning}}\n";
        }
    }

    private final TestablePromptTemplateService service = new TestablePromptTemplateService();

    @Test
    void shouldReplaceVariables() {
        Map<String, String> vars = new HashMap<>();
        vars.put("studentName", "张三");
        vars.put("subject", "数学");
        String result = service.assemble("学生: {{studentName}}, 学科: {{subject}}", vars);
        assertThat(result).isEqualTo("学生: 张三, 学科: 数学");
    }

    @Test
    void shouldHandleNullValue() {
        Map<String, String> vars = new HashMap<>();
        vars.put("key", null);
        String result = service.assemble("值: {{key}}", vars);
        assertThat(result).isEqualTo("值: ");
    }

    @Test
    void shouldInjectMastersDegradationWarning() {
        Map<String, String> base = new HashMap<>();
        base.put("mastersAvailable", "true");
        var vars = service.withMastersDegradation(base);
        assertThat(vars.get("mastersAvailable")).isEqualTo("false");
    }

    @Test
    void shouldBuildPromptForStudentDiagnosis() {
        Map<String, String> vars = new HashMap<>();
        vars.put("studentName", "张三");
        vars.put("studentNo", "S001");
        vars.put("className", "初三(1)班");
        vars.put("subject", "数学");
        vars.put("subgraphText", "## 子图数据");
        vars.put("userQuestion", "分析张三");
        vars.put("weakThreshold", "0.6");
        vars.put("maxHops", "2");
        vars.put("mastersAvailable", "true");

        var pair = service.buildPrompt(QueryIntent.STUDENT_DIAGNOSIS, vars);
        assertThat(pair.systemPrompt()).isNotNull();
        assertThat(pair.userMessage()).contains("张三");
    }

    @Test
    void shouldAddMastersWarningWhenDegraded() {
        Map<String, String> vars = new HashMap<>();
        vars.put("studentName", "张三");
        vars.put("studentNo", "S001");
        vars.put("className", "初三(1)班");
        vars.put("subject", "数学");
        vars.put("subgraphText", "");
        vars.put("userQuestion", "");
        vars.put("weakThreshold", "0.6");
        vars.put("maxHops", "2");
        vars.put("mastersAvailable", "false");

        var pair = service.buildPrompt(QueryIntent.STUDENT_DIAGNOSIS, vars);
        assertThat(pair.userMessage()).contains("融合数据不可用");
    }
}