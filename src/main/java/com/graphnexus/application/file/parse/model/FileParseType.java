package com.graphnexus.application.file.parse.model;

/**
 * 文件解析类型抽象 — 教材和成绩各自实现。
 *
 * <p>教材实现见 {@code TextbookFileType}，成绩实现见 {@code GradeFileType}。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface FileParseType {

    /** 类型标识名称 */
    String name();
}