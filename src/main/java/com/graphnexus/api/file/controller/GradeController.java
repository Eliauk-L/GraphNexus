package com.graphnexus.api.file.controller;

import com.graphnexus.api.file.dto.grade.DeleteResultVO;
import com.graphnexus.api.file.dto.grade.ExamSummaryVO;
import com.graphnexus.api.file.dto.grade.GradeRecordVO;
import com.graphnexus.api.file.dto.grade.GradeUploadResultVO;
import com.graphnexus.application.file.grade.service.GradeUploadService;
import com.graphnexus.application.file.grade.model.ExamSummaryBO;
import com.graphnexus.application.file.grade.model.GradeRecordBO;
import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import com.graphnexus.application.file.grade.service.GradeService;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 成绩处理 REST API 控制器 — 上传/条件查询/删除。
 *
 * @author Jay
 * @date 2026/06/19
 */
@RestController
@RequestMapping("/api/v1/file/grades")
@RequiredArgsConstructor
@Tag(name = "成绩管理", description = "CSV/Excel 成绩上传、条件查询与删除管理")
@PreAuthorize("hasRole('TEACHER')")
public class GradeController {

    private final GradeService gradeService;
    private final GradeUploadService gradeUploadService;

    /**
     * 上传成绩文件（CSV / Excel）。
     */
    @Operation(summary = "上传成绩", description = "上传 CSV 或 Excel 成绩文件（双行表头格式），自动解析并写入 MySQL")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功"),
            @ApiResponse(responseCode = "400", description = "文件格式错误"),
            @ApiResponse(responseCode = "409", description = "考试编号已存在")
    })
    @PostMapping("/upload")
    public ApiResult<GradeUploadResultVO> upload(
            @Parameter(description = "成绩文件（.csv / .xlsx / .xls）", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "学科名称", required = true, example = "数学")
            @RequestParam("subject") String subject
    ) {
        GradeUploadResultBO bo = (GradeUploadResultBO) gradeUploadService.upload(file, subject);
        return ApiResult.success(GradeUploadResultVO.from(bo));
    }

    /**
     * 条件组合查询成绩记录（所有参数可选，支持分页）。
     */
    @Operation(summary = "条件查询成绩", description = "按考试编号/名称、学号/姓名、班级、学科组合查询，支持分页")
    @GetMapping
    public ApiResult<PageResult<GradeRecordVO>> list(
            @Parameter(description = "考试编号（精确匹配）", example = "E20200041")
            @RequestParam(required = false) String examNo,
            @Parameter(description = "考试名称（模糊匹配）", example = "月考")
            @RequestParam(required = false) String examName,
            @Parameter(description = "学号（精确匹配）", example = "S001")
            @RequestParam(required = false) String studentNo,
            @Parameter(description = "学生姓名（模糊匹配）", example = "张三")
            @RequestParam(required = false) String name,
            @Parameter(description = "班级（精确匹配）", example = "一班")
            @RequestParam(required = false) String className,
            @Parameter(description = "学科（精确匹配）", example = "数学")
            @RequestParam(required = false) String subject,
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        PageResult<GradeRecordBO> result = gradeService.queryByConditions(
                examNo, examName, studentNo, name, className, subject, pageNum, pageSize);
        List<GradeRecordVO> voList = result.list().stream().map(GradeRecordVO::from).toList();
        return ApiResult.success(new PageResult<>(voList, result.total(), result.pageNum(), result.pageSize()));
    }

    /**
     * 按考试编号级联删除成绩。
     */
    @Operation(summary = "级联删除成绩", description = "删除整场考试的全部成绩记录，同时清理 Neo4j 图谱数据")
    @DeleteMapping("/exam/{examNo}")
    public ApiResult<DeleteResultVO> deleteGrade(
            @Parameter(description = "考试编号", required = true, example = "E20200041")
            @PathVariable("examNo") String examNo
    ) {
        Object[] result = gradeService.deleteByExamNo(examNo);
        return ApiResult.success(DeleteResultVO.of((String) result[0], (int) result[1]));
    }

    /**
     * 分页查询不重复的考试汇总（管理考试弹窗用）。
     */
    @Operation(summary = "考试汇总", description = "返回按 examNo 去重的考试元数据 + 人数，用于管理考试弹窗")
    @GetMapping("/exams")
    public ApiResult<PageResult<ExamSummaryVO>> listExams(
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小", example = "50")
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        PageResult<ExamSummaryBO> result = gradeService.listDistinctExams(pageNum, pageSize);
        List<ExamSummaryVO> voList = result.list().stream().map(ExamSummaryVO::from).toList();
        return ApiResult.success(new PageResult<>(voList, result.total(), result.pageNum(), result.pageSize()));
    }
}