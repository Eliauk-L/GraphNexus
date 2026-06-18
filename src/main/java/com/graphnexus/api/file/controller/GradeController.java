package com.graphnexus.api.file.controller;

import com.graphnexus.api.file.dto.textbook.DeleteResultVO;
import com.graphnexus.api.file.dto.grade.GradeRecordVO;
import com.graphnexus.api.file.dto.grade.GradeUploadResultVO;
import com.graphnexus.application.file.textbook.model.DeleteResultBO;
import com.graphnexus.application.file.grade.upload.GradeUploadService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 成绩处理 REST API 控制器 — 独立上传/查询/删除。
 *
 * @author Jay
 * @date 2026/06/18
 */
@RestController
@RequestMapping("/api/v1/file/grades")
@RequiredArgsConstructor
@Tag(name = "成绩管理", description = "CSV 成绩上传、查询与删除管理")
public class GradeController {

    private final GradeService gradeService;
    private final GradeUploadService gradeUploadService;

    /**
     * 上传 CSV 成绩文件。
     */
    @Operation(summary = "上传成绩", description = "上传 CSV 成绩文件（双行表头格式），自动解析并写入 MySQL + Neo4j")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = GradeUploadResultVO.class))}),
            @ApiResponse(responseCode = "400", description = "A0004 文件类型不支持"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/upload")
    public ApiResult<GradeUploadResultVO> upload(
            @Parameter(description = "CSV 成绩文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "学科名称", required = true, example = "数学")
            @RequestParam("subject") String subject
    ) {
        GradeUploadResultBO bo = (GradeUploadResultBO) gradeUploadService.upload(file, subject);
        return ApiResult.success(GradeUploadResultVO.from(bo));
    }

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