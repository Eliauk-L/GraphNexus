package com.graphnexus.application.graph.construction.extract;

import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 响应 JSON 解析器 — 宽松模式 + 分层防御。
 *
 * <p>LLM 输出本质是不稳定的文本生成，无法保证严格 JSON 格式。
 * 本组件采用"预处理清理 → 括号匹配提取 → Jackson 宽松解析 → 激进兜底"四层防御，
 * 最大化 JSON 解析成功率。</p>
 *
 * <h3>防御层级</h3>
 * <ol>
 *   <li>BOM / markdown 代码块 / JS 注释清理</li>
 *   <li>括号匹配算法精准提取最外层 JSON 对象（正确处理嵌套和字符串内花括号）</li>
 *   <li>Jackson 宽松模式解析（容忍尾逗号、单引号、无引号字段名、注释、未转义控制字符）</li>
 *   <li>激进正则清理 + 二次解析兜底</li>
 * </ol>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Component
public class ExtractionJsonParser {

    /**
     * 宽松 ObjectMapper — 容忍 LLM 常见的 JSON 格式问题。
     *
     * <p>注意：宽松模式不共享给其他业务使用，避免意外接受非法 JSON。
     * 此 mapper 专用于 LLM 响应解析场景。</p>
     */
    private final ObjectMapper lenientMapper;

    public ExtractionJsonParser() {
        this.lenientMapper = new ObjectMapper()
                // 容忍尾逗号：{"a": 1,}
                .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
                // 容忍 Java 注释：/* ... */ 和 //
                .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true)
                // 容忍单引号字段名和字符串
                .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(), true)
                // 容忍无引号字段名：{name: "value"}
                .configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true)
                // 容忍字符串内未转义控制字符（如 \n）
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                // 忽略未知属性（LLM 可能幻觉出多余字段）
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 解析 LLM 原始响应为 {@link ExtractionRawResult}。
     *
     * @param llmResponse LLM 返回的原始文本
     * @return 解析后的抽取结果
     * @throws BusinessException A0010 — 所有解析尝试均失败
     */
    public ExtractionRawResult parse(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            throw new BusinessException(ErrorCode.A0010, "LLM 返回空响应");
        }

        // === 第 1 层：预处理 ===
        String cleaned = preprocess(llmResponse);

        // === 第 2 层：括号匹配提取 JSON 对象 ===
        String json = extractJsonObject(cleaned);
        if (json == null || json.isBlank()) {
            log.error("无法从 LLM 响应中提取 JSON 对象，原始响应前500字符: {}",
                    llmResponse.substring(0, Math.min(500, llmResponse.length())));
            throw new BusinessException(ErrorCode.A0010, "LLM 响应中未找到合法 JSON 对象");
        }

        // === 第 3 层：Jackson 宽松解析 ===
        try {
            ExtractionRawResult result = lenientMapper.readValue(json, ExtractionRawResult.class);
            log.debug("JSON 宽松解析成功");
            return result;
        } catch (JsonProcessingException e) {
            log.warn("宽松模式解析失败，尝试激进兜底: {}", e.getOriginalMessage());

            // === 第 4 层：激进正则清理 + 二次解析 ===
            try {
                String aggressivelyCleaned = aggressiveClean(json);
                ExtractionRawResult result = lenientMapper.readValue(aggressivelyCleaned, ExtractionRawResult.class);
                log.info("激进兜底解析成功");
                return result;
            } catch (JsonProcessingException ex) {
                log.error("所有 JSON 解析尝试均失败。" +
                                "原始响应前500字符: {}，" +
                                "宽松解析错误: {}，" +
                                "兜底解析错误: {}",
                        llmResponse.substring(0, Math.min(500, llmResponse.length())),
                        e.getOriginalMessage(),
                        ex.getOriginalMessage());
                throw new BusinessException(ErrorCode.A0010,
                        "LLM 返回非合法 JSON: " + ex.getOriginalMessage());
            }
        }
    }

    // ======================== 预处理 ========================

    /**
     * 预处理 LLM 原始响应：去 BOM、提取 markdown 代码块内容、去 JS 注释、去首尾空白。
     */
    String preprocess(String raw) {
        String result = raw;

        // 1. 去除 BOM
        if (result.startsWith("﻿")) {
            result = result.substring(1);
        }

        // 2. 提取 markdown 代码块内容 (```json ... ```)
        result = extractMarkdownCodeBlock(result);

        // 3. 去除 JS 注释
        result = stripJsComments(result);

        return result.trim();
    }

    /**
     * 提取 markdown 代码块内容。
     * 处理 ```json ... ``` 和 ``` ... ``` 两种形式。
     */
    private String extractMarkdownCodeBlock(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int codeStart = trimmed.indexOf('\n');
            if (codeStart > 0) {
                String afterFence = trimmed.substring(codeStart + 1);
                int closingFence = afterFence.lastIndexOf("```");
                if (closingFence >= 0) {
                    return afterFence.substring(0, closingFence);
                }
                // 没有闭合的 ```，取 fence 之后全部内容
                return afterFence;
            }
        }
        return raw;
    }

    /**
     * 移除 JavaScript 风格的注释（{@code //} 单行 和 {@literal /}* *{@literal /} 多行）。
     */
    String stripJsComments(String raw) {
        // 先移除多行注释 /* ... */（DOTALL 模式使 . 匹配换行符）
        String result = raw.replaceAll("(?s)/\\*.*?\\*/", "");
        // 再移除单行注释 //（后面到行尾）
        result = result.replaceAll("//[^\n]*", "");
        return result;
    }

    // ======================== JSON 对象提取 ========================

    /**
     * 用括号匹配算法提取最外层完整 JSON 对象。
     *
     * <p>相比正则 {@code ^[^{]* / [^}]*$}，此方法能正确处理：
     * <ul>
     *   <li>JSON 内部嵌套对象（如 {@code "metadata": {"key": "val"}}）</li>
     *   <li>字符串内的花括号（如 {@code "text": "a{b}c"}）</li>
     *   <li>转义引号（如 {@code "text": "he said \"hello\""}）</li>
     * </ul></p>
     *
     * @param raw 预处理后的文本
     * @return 最外层 JSON 对象字符串，未找到则返回 null
     */
    String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        if (start < 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < raw.length(); i++) {
            char ch = raw.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (ch == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (ch == '"' && !inString) {
                inString = true;
                continue;
            }
            if (ch == '"' && inString) {
                inString = false;
                continue;
            }

            // 单引号字符串（已经 stripJsComments 处理过，这里是 LLM 可能用的单引号 JSON）
            if (ch == '\'' && !inString) {
                inString = true;
                continue;
            }
            if (ch == '\'' && inString) {
                inString = false;
                continue;
            }

            if (inString) {
                continue;
            }

            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }

        // 括号未闭合（LLM 输出被截断），返回已找到的最大范围
        log.warn("JSON 对象括号未闭合，可能 LLM 输出被截断");
        return raw.substring(start);
    }

    // ======================== 激进兜底 ========================

    /**
     * 激进清理：移除尾逗号 + 再次尝试去除非 JSON 前缀/后缀。
     * 在宽松 ObjectMapper 仍解析失败时作为最后一层防御。
     */
    private String aggressiveClean(String json) {
        // 移除 } 和 ] 前的尾逗号
        String cleaned = json.replaceAll(",(\\s*[}\\]])", "$1");

        // 再次尝试去除非 JSON 首尾字符（兜底当前逻辑）
        cleaned = cleaned.replaceAll("^[^{]*", "")
                .replaceAll("[^}]*$", "");

        return cleaned.trim();
    }
}