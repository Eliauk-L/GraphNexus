package com.graphnexus.application.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 成绩上传结果 BO（L2 层）。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeUploadResultBO {

    /** 考试编号 */
    private String examNo;

    /** 考试名称 */
    private String examName;

    /** 考试日期 */
    private LocalDate examDate;

    /** 学科 */
    private String subject;

    /** 学生人数 */
    private int studentCount;

    /** 题目数量 */
    private int questionCount;

    /** 考查知识点名称列表（去重） */
    private List<String> knowledgePoints;

    /** MinIO 存储路径 */
    private String minioPath;

    /** CSV 文件 MD5 */
    private String csvMd5;
}