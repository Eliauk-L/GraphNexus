package com.graphnexus.application.file.parse.model;

/**
 * 文件解析类型枚举。
 *
 * @author Jay
 * @date 2026/06/15
 */
public enum FileParseType {

    /** CSV 成绩文件 */
    CSV_GRADE,

    /** 文档类文件（PDF/TXT 及未来文档格式） */
    DOCUMENT,

    /** 纯文本文件（.txt） */
    TXT
}