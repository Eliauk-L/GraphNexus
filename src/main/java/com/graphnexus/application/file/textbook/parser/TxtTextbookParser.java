package com.graphnexus.application.file.textbook.parser;

import com.graphnexus.application.file.parse.TextbookParser;
import com.graphnexus.application.file.textbook.model.TextbookFileType;
import com.graphnexus.application.file.parse.FileParseType;
import com.graphnexus.application.file.parse.ParseResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * TXT 纯文本文件解析器 — 实现 {@link TextbookParser} 接口。
 *
 * <p>支持 UTF-8 和 GBK 编码，直接读取文本内容，不做版面分析。
 * 文本内容原样传递给 LLM 做知识抽取，走与 PDF 相同的文档处理链路。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
public class TxtTextbookParser implements TextbookParser {

    @Override
    public FileParseType supportedType() {
        return TextbookFileType.TXT;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".txt");
    }

    @Override
    public ParseResult parse(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new BusinessException(ErrorCode.A0004, "TXT 文件为空", "上传的文件内容为空，无法解析");
        }

        String content = tryDecode(rawBytes);

        // 去除 UTF-8 BOM（Excel 导出的 TXT 首字符常为 U+FEFF）
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }

        log.info("TXT 解析完成: textLength={}", content.length());
        return new ParseResult(content, 1, Map.of());
    }

    /**
     * 尝试 UTF-8 → GBK 解码（与 CsvGradeParser 同款策略）。
     */
    private String tryDecode(byte[] bytes) {
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return new String(bytes, Charset.forName("GBK"));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.A0013,
                    "TXT 编码解码失败，请使用 UTF-8 或 GBK 编码");
        }
    }
}