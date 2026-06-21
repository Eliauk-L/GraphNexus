package com.graphnexus.application.file.parse.model;

import java.io.InputStream;

/**
 * 统一文件解析请求 — 替代各类 Parser 的散装参数。
 *
 * @param inputStream     文件输入流
 * @param originalFilename 原始文件名（含扩展名，用于类型判断）
 * @param subject          学科/业务元数据
 * @param rawBytes         预读的完整字节数组（用于 MD5 判重，避免重复读流）
 * @author Jay
 * @date 2026/06/15
 */
public record FileParseRequest(
        InputStream inputStream,
        String originalFilename,
        String subject,
        byte[] rawBytes
) {}