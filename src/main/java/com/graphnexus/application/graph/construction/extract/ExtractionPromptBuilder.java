package com.graphnexus.application.graph.construction.extract;

import com.graphnexus.application.graph.construction.extract.registry.EntityRelationType;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandler;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandlerRegistry;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.node.EntityType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 抽取 Prompt 构建器 — 从 classpath md 加载模板并装配段落（D1/D2/D3/D4/D5）。
 *
 * <p>沿用 ADR-011 的 {@code classpath:/prompts/*.md} + {@code {{var}}} 范式，自建轻量 loader
 * （不共用 PromptTemplateService——其包级私有且与 query intent 耦合，跨模块复用收益不抵重构风险，见 D1）。
 * 类型段由枚举/注册表派生，few-shot 按学科切换 + 默认回退。</p>
 *
 * <p>Prompt 结构见 ADR-023：角色设定 + 实体类型段（EntityType 派生）+ 关系类型段（EntityRelationType 派生）
 * + 扩展节点段（NodeHandler 派生）+ Few-shot（按 subject）+ JSON Schema 约束。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionPromptBuilder {

    private static final String TEMPLATE_BASE_PATH = "classpath:/prompts/";
    private static final String SYSTEM_TEMPLATE = "extraction-system";
    private static final String USER_TEMPLATE = "extraction-user";
    private static final String FEWSHOT_DEFAULT = "extraction-fewshot-default";
    private static final String FEWSHOT_PREFIX = "extraction-fewshot-";

    /** subject 中文 → few-shot 文件 key 的映射（v1 仅数学，其余回退 default） */
    private static final Map<String, String> SUBJECT_FEWSHOT_KEYS = Map.of(
            "数学", "math");

    private final ResourceLoader resourceLoader;
    private final ExtractionNodeHandlerRegistry nodeHandlerRegistry;

    /** 模板内容缓存（启动期首次加载后常驻，避免每次抽取读盘） */
    private volatile String systemTemplate;
    private volatile String userTemplate;
    private final Map<String, String> fewShotCache = new HashMap<>();

    /**
     * 构建 System Prompt（角色 + 类型段 + 扩展段 + Few-shot + 格式约束）。
     *
     * @param subject 文档学科，用于 few-shot 分域（D5）；null/未知回退 default
     */
    public String buildSystemPrompt(String subject) {
        String template = loadCachedTemplate(SYSTEM_TEMPLATE, () -> systemTemplate, v -> systemTemplate = v);
        Map<String, String> sections = new HashMap<>();
        sections.put("{{entityTypesSection}}", buildEntityTypesSection());
        sections.put("{{relationTypesSection}}", buildRelationTypesSection());
        sections.put("{{extensionNodeSections}}", buildExtensionNodeSections());
        sections.put("{{fewShotSection}}", loadFewShot(subject));
        String prompt = assemble(template, sections);
        log.debug("System Prompt 构建完成: subject={}, length={}", subject, prompt.length());
        return prompt;
    }

    /** 向后兼容：无 subject 时回退默认 few-shot */
    public String buildSystemPrompt() {
        return buildSystemPrompt(null);
    }

    /**
     * 构建 User Message — 拼接文档信息和待抽取文本。
     */
    public String buildUserMessage(String docName, String subject, Integer pageCount, String textContent) {
        String template = loadCachedTemplate(USER_TEMPLATE, () -> userTemplate, v -> userTemplate = v);
        Map<String, String> vars = new HashMap<>();
        vars.put("{{docName}}", nullToEmpty(docName));
        vars.put("{{pageCount}}", pageCount == null ? "" : String.valueOf(pageCount));
        vars.put("{{subject}}", nullToEmpty(subject));
        vars.put("{{textContent}}", nullToEmpty(textContent));
        return assemble(template, vars);
    }

    // ======================== 段落派生 ========================

    /**
     * 实体类型段 — 由 {@link EntityType} 枚举派生（D2 / AC-2 单一来源）。
     */
    String buildEntityTypesSection() {
        return java.util.Arrays.stream(EntityType.values())
                .map(t -> "- " + t.getValue() + "：" + t.getDisplayName()
                        + "（如\"" + t.getExample() + "\"）")
                .collect(Collectors.joining("\n"));
    }

    /**
     * 关系类型段 — 由 {@link EntityRelationType} 枚举派生（D3 / AC-3 单一来源）。
     */
    String buildRelationTypesSection() {
        return java.util.Arrays.stream(EntityRelationType.values())
                .map(t -> "- " + t.getValue() + "：" + t.getDescription())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 扩展节点段 — 由 {@link ExtractionNodeHandlerRegistry} 派生（D4）。
     * 生产无注册 handler 时返回空串，与原 prompt 一致。
     */
    String buildExtensionNodeSections() {
        if (nodeHandlerRegistry.all().isEmpty()) {
            return "";
        }
        return "\n" + nodeHandlerRegistry.all().stream()
                .map(ExtractionNodeHandler::promptSchema)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Few-shot 段 — 按 subject 选对应学科示例，未命中回退 default（D5 / AC-5）。
     */
    String loadFewShot(String subject) {
        String key = subject == null ? null : SUBJECT_FEWSHOT_KEYS.get(subject);
        if (key != null) {
            try {
                return fewShotCache.computeIfAbsent(FEWSHOT_PREFIX + key, this::loadTemplateRaw);
            } catch (BusinessException e) {
                log.warn("学科 {} 的 few-shot 模板缺失，回退 default: {}", subject, e.getMessage());
            }
        }
        return fewShotCache.computeIfAbsent(FEWSHOT_DEFAULT, this::loadTemplateRaw);
    }

    // ======================== 加载与替换 ========================

    /**
     * 加载模板（缓存）。首次访问时读盘，后续命中缓存。
     */
    private synchronized String loadCachedTemplate(String name, java.util.function.Supplier<String> cacheRef,
                                                   java.util.function.Consumer<String> cacheSetter) {
        String cached = cacheRef.get();
        if (cached != null) {
            return cached;
        }
        String content = loadTemplateRaw(name);
        cacheSetter.accept(content);
        return content;
    }

    /**
     * 从 classpath 加载模板原文。
     *
     * @throws BusinessException 模板不存在时 C0001
     */
    private String loadTemplateRaw(String name) {
        String location = TEMPLATE_BASE_PATH + name + ".md";
        try {
            var resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new BusinessException(ErrorCode.C0001,
                        "Prompt 模板不存在: " + name + "（路径: " + location + "）");
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载 Prompt 模板失败: {} ({})", name, e.getMessage());
            throw new BusinessException(ErrorCode.C0001, "加载 Prompt 模板失败: " + name);
        }
    }

    /**
     * 执行模板变量替换（ADR-011 纯字符串替换，不引入模板引擎）。
     */
    private String assemble(String template, Map<String, String> vars) {
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        if (result.contains("{{") && result.contains("}}")) {
            log.warn("Prompt 中可能存在未替换的占位符: {}", result.substring(
                    result.indexOf("{{"), Math.min(result.indexOf("{{") + 30, result.length())));
        }
        return result;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
