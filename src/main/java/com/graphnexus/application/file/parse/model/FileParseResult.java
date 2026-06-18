package com.graphnexus.application.file.parse.model;

/**
 * 统一文件解析结果 — 泛型 payload 让各解析器返回自己的领域对象。
 *
 * @param <T>       payload 类型（如 {@code CsvParsePayload}、{@code PdfParsePayload}）
 * @param payload   解析出的领域对象
 * @param parseType 文件类型枚举
 * @author Jay
 * @date 2026/06/15
 */
public record FileParseResult<T>(
        T payload,
        FileParseType parseType
) {}