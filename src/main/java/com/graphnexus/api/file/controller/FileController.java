package com.graphnexus.api.file.controller;

import com.graphnexus.api.file.dto.core.DeleteResultVO;
import com.graphnexus.api.file.dto.core.FileVO;
import com.graphnexus.api.file.dto.upload.GradeRecordVO;
import com.graphnexus.api.file.dto.upload.GradeUploadResultVO;
import com.graphnexus.api.file.dto.parse.ParseResultVO;
import com.graphnexus.api.file.dto.core.UpdateFileRequest;
import com.graphnexus.application.file.core.model.DeleteResultBO;
import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.upload.model.GradeUploadResultBO;
import com.graphnexus.application.file.parse.parser.FileParserRegistry;
import com.graphnexus.application.file.core.service.FileService;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.core.model.UpdateFileBO;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档处理 REST API 控制器。
 *
 * @author Jay
 * @date 2026/06/12
 */
@RestController
@RequestMapping("/api/v1/file/document")
@RequiredArgsConstructor
@Tag(name = "文档处理", description = "PDF 教辅上传解析、CSV 成绩导入与成绩查询管理")
public class FileController {

    private final FileService fileService;
    private final FileParserRegistry fileParserRegistry;

    /**
     * 上传文件（PDF / CSV 统一入口，策略+工厂路由）。
     */
    @Operation(summary = "上传文件", description = "PDF/CSV 统一上传入口。PDF 上传后返回文档元数据，CSV 上传后自动解析成绩并入库 Neo4j 图 + MySQL exam_record 表。按文件扩展名自动路由到对应处理器")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功",
                    content = {@Content(mediaType = "application/json", schema = @Schema(oneOf = {FileVO.class, GradeUploadResultVO.class}))}),
            @ApiResponse(responseCode = "400", description = "A0002 参数校验失败 / A0004 文件类型不支持 / A0011 CSV 格式错误 / A0012 CSV 缺少必要列 / A0013 CSV 编码异常"),
            @ApiResponse(responseCode = "409", description = "A0007 该学科下已存在相同内容文档"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/upload")
    public ApiResult<?> upload(
            @Parameter(description = "上传文件（支持 PDF/CSV）", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "学科名称（如 数学、语文、英语）", required = true, example = "数学")
            @RequestParam("subject") String subject
    ) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        var parser = fileParserRegistry.getParser(filename);

        if (parser.isPresent() && parser.get().supportedType().name().equals("CSV_GRADE")) {
            GradeUploadResultBO bo = fileService.uploadGradeCsv(file, subject);
            return ApiResult.success(GradeUploadResultVO.from(bo));
        }

        // 默认走 PDF 链路
        FileBO bo = fileService.upload(file, subject);
        return ApiResult.success(FileVO.from(bo));
    }

    /**
     * 触发文档解析。
     */
    @Operation(summary = "触发文档解析", description = "对已上传的 PDF 文档执行 MinerU v4 精准解析（主），失败自动 fallback 到 PDFBox（兜底）。解析完成后更新文档状态为 COMPLETED 并存储文本内容")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解析完成，返回文本内容与页数"),
            @ApiResponse(responseCode = "400", description = "A0008 文档文本为空 / A0009 文档状态不允许解析"),
            @ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / C0001 MinerU API 调用失败")
    })
    @PostMapping("/{id}/process")
    public ApiResult<ParseResultVO> process(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        ParseResult result = fileService.process(id);
        return ApiResult.success(ParseResultVO.from(id, result));
    }

    /**
     * 分页查询文档列表。
     */
    @Operation(summary = "分页查询文档列表", description = "按上传时间倒序分页返回文档列表。页码从 1 开始")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分页文档列表"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping
    public ApiResult<PageResult<FileVO>> list(
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小", example = "10")
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<FileBO> page = fileService.listDocuments(pageNum, pageSize);
        return ApiResult.success(PageResult.of(page.map(FileVO::from)));
    }

    /**
     * 查询单个文档。
     */
    @Operation(summary = "查询文档详情", description = "按文档 ID 查询单条文档的完整元数据（含文件大小、页数、状态等）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "文档详情"),
            @ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/{id}")
    public ApiResult<FileVO> get(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        FileBO bo = fileService.getDocument(id);
        return ApiResult.success(FileVO.from(bo));
    }

    /**
     * 更新文档名称。
     */
    @Operation(summary = "更新文档名称", description = "修改文档的显示名称。仅可修改名称字段，其他字段不可变")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新后的文档信息"),
            @ApiResponse(responseCode = "400", description = "A0002 名称不能为空"),
            @ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PutMapping("/{id}")
    public ApiResult<FileVO> update(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable("id") Long id,
            @Parameter(description = "更新请求体", required = true)
            @RequestBody @Valid UpdateFileRequest request
    ) {
        UpdateFileBO bo = new UpdateFileBO();
        bo.setName(request.getName());
        FileBO updated = fileService.updateDocument(id, bo);
        return ApiResult.success(FileVO.from(updated));
    }

    /**
     * 删除文档（逻辑删除 + MinIO 物理清除）。
     */
    @Operation(summary = "删除文档", description = "级联删除：标记 DELETING 中间状态 → 删除 MinIO 文件 → 清除 Neo4j 关联子图（EntityNode + 边） → 标记 DELETED 终态。保留共享 KnowledgePoint 节点不级联。幂等操作，重复删除返回成功")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功（data=null）"),
            @ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        fileService.deleteDocument(id);
        return ApiResult.success(null);
    }

    // ======================== 成绩端点 ========================

    /**
     * 按考试编号查询成绩列表（AC-4）。
     */
    @Operation(summary = "按考试编号查询成绩", description = "返回指定考试编号下的全部学生成绩记录列表，含各题得分明细（JSON）。数据来源：MySQL exam_record 表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成绩记录列表"),
            @ApiResponse(responseCode = "404", description = "A0014 考试编号不存在"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/grade/exam/{examNo}")
    public ApiResult<List<GradeRecordVO>> queryGrade(
            @Parameter(description = "考试编号（来源于 CSV 第 1 行考试编号列）", required = true, example = "E20200041")
            @PathVariable("examNo") String examNo
    ) {
        var records = fileService.queryGradeByExam(examNo);
        List<GradeRecordVO> result = records.stream()
                .map(GradeRecordVO::from)
                .toList();
        return ApiResult.success(result);
    }

    /**
     * 按考试编号级联删除成绩（AC-7）。
     */
    @Operation(summary = "级联删除成绩", description = "按考试编号级联删除：MySQL exam_record 记录 + MinIO CSV 文件 + Neo4j ATTENDED/TESTED 边。保留 Student 和 KnowledgePoint 共享节点不级联。幂等操作")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功，含删除的 MySQL 记录数、MinIO 文件路径、Neo4j 边数"),
            @ApiResponse(responseCode = "404", description = "A0015 考试记录不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @DeleteMapping("/grade/exam/{examNo}")
    public ApiResult<DeleteResultVO> deleteGrade(
            @Parameter(description = "考试编号", required = true, example = "E20200041")
            @PathVariable("examNo") String examNo
    ) {
        DeleteResultBO bo = fileService.deleteGradeByExamNo(examNo);
        return ApiResult.success(DeleteResultVO.from(bo));
    }
}