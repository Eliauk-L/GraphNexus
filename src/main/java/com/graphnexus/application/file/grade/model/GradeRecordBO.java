package com.graphnexus.application.file.grade.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条成绩记录 BO（L2 层，用于成绩查询返回）。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeRecordBO {

    private Long id;
    private String studentNo;
    private String name;
    private String className;
    private String examNo;
    private String examName;
    private String subject;
    private Integer totalScore;
    private Integer classRank;
    private String scoreDetails; // JSON 字符串
}