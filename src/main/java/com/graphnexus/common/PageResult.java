package com.graphnexus.common;

import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应体（不可变 record）。
 *
 * <p>所有分页查询接口返回数据时必须使用此类包装。</p>
 *
 * @param list     数据列表
 * @param total    总条数
 * @param pageNum  当前页码（从 1 开始）
 * @param pageSize 每页大小
 * @param <T>      数据项类型
 * @author Jay
 * @date 2026/06/11
 */
public record PageResult<T>(
        List<T> list,
        long total,
        int pageNum,
        int pageSize
) {

    /**
     * 从 Spring Data {@link Page} 对象构造。
     *
     * @param page Spring Data 分页结果
     * @param <T>  数据项类型
     * @return PageResult
     */
    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,   // Spring Data page number 从 0 开始，转换为从 1 开始
                page.getSize()
        );
    }

    /**
     * 空结果（用于参数校验失败或查询无数据时返回）。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @param <T>      数据项类型
     * @return 空的 PageResult
     */
    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(
                Collections.emptyList(),
                0L,
                pageNum,
                pageSize
        );
    }
}