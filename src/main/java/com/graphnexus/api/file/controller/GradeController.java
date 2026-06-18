package com.graphnexus.api.file.controller;

import com.graphnexus.api.file.dto.core.DeleteResultVO;
import com.graphnexus.api.file.dto.upload.GradeRecordVO;
import com.graphnexus.api.file.dto.upload.GradeUploadResultVO;
import com.graphnexus.application.file.core.model.DeleteResultBO;
import com.graphnexus.application.file.upload.model.GradeRecordBO;
import com.graphnexus.application.file.upload.model.GradeUploadResultBO;
import com.graphnexus.application.file.upload.service.GradeService;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成绩处理 REST API 控制器 — 仅查询/删除。
 *
 * <p>上传入口统一在 {@link FileController}。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@RestController
@RequestMapping("/api/v1/file/grade")
@RequiredArgsConstructor
@Tag(name = "成绩管理", description = "CSV 成绩查询与删除管理")
public class GradeController {

    private final GradeService gradeService;

    /**
     * 分页查询成绩列表（按考试分组）。
     */
    @Operation(summary = "分页查询成绩列表")
    @GetMapping
    public ApiResult<PageResult<GradeUploadResultVO>> list(
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小", example = "10")
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<GradeUploadResultBO> page = gradeService.listExams(pageNum, pageSize);
        return ApiResult.success(PageResult.of(page.map(GradeUploadResultVO::from)));
    }

    /**
     * 按考试编号查询成绩列表。
     */
    @Operation(summary = "按考试编号查询成绩")
    @GetMapping("/exam/{examNo}")
    public ApiResult<List<GradeRecordVO>> queryGrade(
            @Parameter(description = "考试编号", required = true, example = "E20200041")
            @PathVariable("examNo") String examNo
    ) {
        List<GradeRecordBO> records = gradeService.queryByExam(examNo);
        List<GradeRecordVO> result = records.stream()
                .map(GradeRecordVO::from)
                .toList();
        return ApiResult.success(result);
    }

    /**
     * 按考试编号级联删除成绩。
     */
    @Operation(summary = "级联删除成绩")
    @DeleteMapping("/exam/{examNo}")
    public ApiResult<DeleteResultVO> deleteGrade(
            @Parameter(description = "考试编号", required = true, example = "E20200041")
            @PathVariable("examNo") String examNo
    ) {
        DeleteResultBO bo = gradeService.deleteByExamNo(examNo);
        return ApiResult.success(DeleteResultVO.from(bo));
    }
}