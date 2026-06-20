package com.graphnexus.application.file.grade.parser;

import com.graphnexus.application.file.grade.model.GradeFileType;
import com.graphnexus.application.file.parse.model.FileParseType;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * CSV 成绩文件解析器 — 双行表头格式。
 *
 * <p>继承 {@link AbstractGradeParser}，仅负责 CSV 编码处理与 Commons CSV 行读取，
 * 列分类、校验、知识点提取、分数解析等公共逻辑由父类提供。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Slf4j
@Component
public class CsvGradeParser extends AbstractGradeParser {

    private List<CSVRecord> records;

    @Override
    public FileParseType supportedType() {
        return GradeFileType.CSV;
    }

    @Override
    public String businessType() {
        return BIZ_GRADE;
    }

    @Override
    public Set<String> supportedExtensions() {
        return Set.of(".csv");
    }

    // ======================== 模板方法实现 ========================

    @Override
    protected void init(byte[] rawBytes) throws Exception {
        String content = tryDecode(rawBytes);
        // 去除 UTF-8 BOM
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            content = content.substring(1);
        }
        CSVParser parser = CSVParser.parse(content, CSVFormat.DEFAULT.withTrim());
        records = parser.getRecords();
    }

    @Override
    protected void cleanup() {
        records = null;
    }

    @Override
    protected int getRowCount() {
        return records.size();
    }

    @Override
    protected String getCellValue(int row, int col) {
        CSVRecord csvRow = records.get(row);
        return col < csvRow.size() ? csvRow.get(col) : "";
    }

    @Override
    protected String getParserName() {
        return "CSV";
    }

    @Override
    protected void validateDataRow(int row, int expectedCols) {
        CSVRecord csvRow = records.get(row);
        if (csvRow.size() != expectedCols) {
            throw new BusinessException(ErrorCode.A0011,
                    "第 " + (row + 1) + " 行列数不一致：期望 " + expectedCols
                            + " 列，实际 " + csvRow.size() + " 列");
        }
    }

    // ======================== CSV 特有逻辑 ========================

    /**
     * 尝试 UTF-8 → GBK 解码。
     */
    private String tryDecode(byte[] bytes) {
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        try {
            return new String(bytes, Charset.forName("GBK"));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.A0013,
                    "CSV 编码解码失败，请使用 UTF-8 或 GBK 编码");
        }
    }
}