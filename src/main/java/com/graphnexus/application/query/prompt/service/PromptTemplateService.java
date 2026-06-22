package com.graphnexus.application.query.prompt.service;

import com.graphnexus.application.query.chat.model.QueryIntent;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt 模板服务 — 从 classpath 加载 Markdown 模板文件，执行变量替换，组装最终 Prompt。
 *
 * <p>模板文件位于 {@code classpath:/prompts/}，命名规则：{@code {intent小写}-{system|user}.md}。
 * 占位符格式：{@code {{variableName}}}。不引入模板引擎，纯字符串替换。</p>
 *
 * <p>设计决策见 ADR-011。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final ResourceLoader resourceLoader;

    private static final String TEMPLATE_BASE_PATH = "classpath:/prompts/";
    private static final String MASTERS_DEGRADATION_WARNING =
            "\n\n⚠️ **数据质量提示**：融合数据不可用，以下掌握度为原始考试得分率（未做时间衰减加权），"
            + "可能与实际掌握水平存在偏差。建议执行融合后重新诊断以获得更准确的分析结果。\n";

    /**
     * Prompt 对 — system prompt 和 user message。
     */
    public record PromptPair(String systemPrompt, String userMessage) {}

    /**
     * 按意图组装完整的 Prompt 对。
     *
     * @param intent    查询意图
     * @param variables 模板变量 Map（key 不含 {{}} 前缀后缀）
     * @return Prompt 对（system + user）
     */
    public PromptPair buildPrompt(QueryIntent intent, Map<String, String> variables) {
        String prefix = intent.name().toLowerCase().replace('_', '-');
        String systemPrompt = loadTemplate(prefix + "-system");
        String userTemplate = loadTemplate(prefix + "-user");
        String userMessage = assemble(userTemplate, variables);

        // MASTERS 降级警告注入
        if ("false".equals(variables.get("mastersAvailable"))) {
            userMessage += MASTERS_DEGRADATION_WARNING;
        }

        log.debug("Prompt 组装完成: intent={}, systemPrompt={}chars, userMessage={}chars",
                intent, systemPrompt.length(), userMessage.length());
        return new PromptPair(systemPrompt, userMessage);
    }

    /**
     * 加载 classpath 下的模板文件。
     *
     * @param name 模板文件名（不含路径前缀），如 "student-diagnosis-system"
     * @return 模板文件全文
     * @throws BusinessException 模板文件不存在时抛 C0001
     */
    public String loadTemplate(String name) {
        String location = TEMPLATE_BASE_PATH + name + ".md";
        try {
            var resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new BusinessException(ErrorCode.C0001,
                        "Prompt 模板不存在: " + name + "（路径: " + location + "）");
            }
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("加载模板: {} ({} chars)", name, content.length());
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载模板失败: {} ({})", name, e.getMessage());
            throw new BusinessException(ErrorCode.C0001, "加载 Prompt 模板失败: " + name);
        }
    }

    /**
     * 执行模板变量替换。
     *
     * <p>遍历 variables Map，将 {@code {{key}}} 替换为对应 value。
     * null value 替换为空字符串。</p>
     *
     * @param template  含占位符的模板字符串
     * @param variables 变量名 → 值的映射（不含 {{}}）
     * @return 替换后的字符串
     */
    String assemble(String template, Map<String, String> variables) {
        String result = template;
        for (var entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        // 检查是否有未替换的占位符并警告
        if (result.contains("{{") && result.contains("}}")) {
            log.warn("模板中可能存在未替换的占位符: {}", result.substring(
                    result.indexOf("{{"), Math.min(result.indexOf("{{") + 30, result.length())));
        }
        return result;
    }

    /**
     * 构建 MASTERS 降级场景下的变量 Map（在原有变量基础上追加 mastersAvailable=false）。
     */
    public Map<String, String> withMastersDegradation(Map<String, String> base) {
        Map<String, String> vars = new HashMap<>(base);
        vars.put("mastersAvailable", "false");
        return vars;
    }

    /**
     * 按输出格式组装 Prompt 对。
     *
     * @param intent    查询意图
     * @param variables 模板变量 Map
     * @param format    输出格式 ("markdown" | "html-svg")
     * @return Prompt 对（system + user）
     */
    public PromptPair buildPrompt(QueryIntent intent, Map<String, String> variables, String format) {
        String prefix = intent.name().toLowerCase().replace('_', '-');
        String systemTemplate = loadTemplateWithFormat(prefix + "-system", format);
        String userTemplate = loadTemplateWithFormat(prefix + "-user", format);
        String userMessage = assemble(userTemplate, variables);

        if ("false".equals(variables.get("mastersAvailable"))) {
            userMessage += MASTERS_DEGRADATION_WARNING;
        }

        log.debug("Prompt 组装完成: intent={}, format={}, systemPrompt={}chars, userMessage={}chars",
                intent, format, systemTemplate.length(), userMessage.length());
        return new PromptPair(systemTemplate, userMessage);
    }

    /**
     * 按格式加载模板文件。
     * format="markdown" → 加载 {baseName}.md
     * format="html-svg" → 先尝试 {baseName}-html.md，失败则 fallback 到 {baseName}.md
     */
    private String loadTemplateWithFormat(String baseName, String format) {
        if (!"markdown".equals(format)) {
            String formattedName = baseName + "-" + format;
            try {
                return loadTemplate(formattedName);
            } catch (BusinessException e) {
                log.debug("格式模板不存在，fallback 到默认模板: {}", formattedName);
            }
        }
        return loadTemplate(baseName);
    }

    /**
     * 构建意图列表文本 — 遍历 {@link QueryIntent} 枚举动态生成，
     * 用于注入意图分类 prompt 的 {{intentList}} 占位符。
     */
    public String buildIntentList() {
        return Arrays.stream(QueryIntent.values())
                .map(i -> "- " + i.name() + " — " + i.getDescription())
                .collect(Collectors.joining("\n"));
    }
}