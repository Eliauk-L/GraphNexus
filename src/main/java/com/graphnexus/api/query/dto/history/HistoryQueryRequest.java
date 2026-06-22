package com.graphnexus.api.query.dto.history;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 历史诊断记录查询请求。
 *
 * <p>所有筛选参数均为可选。不传则不加该条件。</p>
 *
 * @param studentName 学生姓名（模糊匹配）
 * @param studentNo   学号（精确匹配）
 * @param subject     学科（精确匹配）
 * @param status      状态（COMPLETED/FAILED）
 * @param startDate   查询起始日期（含）
 * @param endDate     查询结束日期（含）
 * @param pageNum     页码（从 1 开始）
 * @param pageSize    每页大小
 * @author Jay
 * @date 2026/06/22
 */
public record HistoryQueryRequest(
        String studentName,
        String studentNo,
        String subject,
        String status,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @Min(1)
        Integer pageNum,

        @Min(1) @Max(100)
        Integer pageSize
) {

    /** 默认构造：页码 1，每页 10 */
    public HistoryQueryRequest {
        if (pageNum == null) pageNum = 1;
        if (pageSize == null) pageSize = 10;
    }

    /** 用于导出请求（不含分页参数）时复用筛选字段 */
    public HistoryQueryRequest withDefaults() {
        return new HistoryQueryRequest(
                studentName, studentNo, subject, status,
                startDate, endDate, 1, Integer.MAX_VALUE
        );
    }
}