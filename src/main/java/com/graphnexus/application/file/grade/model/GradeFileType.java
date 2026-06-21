package com.graphnexus.application.file.grade.model;

import com.graphnexus.application.file.parse.model.FileParseType;

/**
 * 成绩文件格式枚举，表示具体的文件扩展名类型。
 * 业务类型统一由 {@code FileParser.BIZ_GRADE} 表达，
 * 格式细节由 {@code FileParserRegistry} 按扩展名路由。
 *
 * @author Jay
 * @date 2026/06/19
 */
public enum GradeFileType implements FileParseType {
    /** CSV 格式（.csv） */
    CSV,
    /** Excel 格式（.xlsx / .xls） */
    EXCEL
}