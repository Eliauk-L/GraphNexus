package com.graphnexus.api.file.controller;

import com.graphnexus.api.file.dto.core.DeleteResultVO;
import com.graphnexus.api.file.dto.core.FileVO;
import com.graphnexus.api.file.dto.upload.GradeUploadResultVO;
import com.graphnexus.api.file.dto.parse.ParseResultVO;
import com.graphnexus.api.file.dto.core.UpdateFileRequest;
import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.upload.model.GradeUploadResultBO;
import com.graphnexus.application.file.parse.parser.FileParserRegistry;
import com.graphnexus.application.file.core.service.FileService;
import com.graphnexus.application.file.core.pipeline.DocumentProcessingPipeline;
import com.graphnexus.application.file.core.pipeline.GradeProcessingPipeline;
import com.graphnexus.application.file.parse.model.FileParseType;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.core.model.UpdateFileBO;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
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
@Tag(name = "文档处理", description = "文档上传解析管理（PDF/TXT/CSV 统一入口）")
public class FileController {

    private final FileService fileService;
    private final FileParserRegistry fileParserRegistry;
    private final DocumentProcessingPipeline documentPipeline;
    private final GradeProcessingPipeline gradePipeline;

    /**
     * 上传文件（PDF/TXT/CSV 统一入口，枚举 switch 路由）。
     */
    @Operation(summary = "上传文件", description = "统一上传入口。按文件扩展名自动路由到 DocumentPipeline 或 GradePipeline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功",
                    content = {@Content(mediaType = "application/json", schema = @Schema(oneOf = {FileVO.class, GradeUploadResultVO.class}))}),
            @ApiResponse(responseCode = "400", description = "A0004 文件类型不支持"),
            @ApiResponse(responseCode = "409", description = "A0007 该学科下已存在相同内容文档"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/upload")
    public ApiResult<?> upload(
            @Parameter(description = "上传文件（支持 PDF/TXT/CSV）", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "学科名称", required = true, example = "数学")
            @RequestParam("subject") String subject
    ) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        var parser = fileParserRegistry.getParser(filename);

        if (parser.isEmpty()) {
            throw new BusinessException(ErrorCode.A0004, "不支持的文件类型: " + filename);
        }

        FileParseType type = parser.get().supportedType();
        return switch (type) {
            case DOCUMENT -> {
                FileBO bo = (FileBO) documentPipeline.process(file, subject);
                yield ApiResult.success(FileVO.from(bo));
            }
            case CSV_GRADE -> {
                GradeUploadResultBO bo = (GradeUploadResultBO) gradePipeline.process(file, subject);
                yield ApiResult.success(GradeUploadResultVO.from(bo));
            }
            case TXT -> throw new BusinessException(ErrorCode.A0004,
                    "TXT 类型应由 DOCUMENT pipeline 处理");
        };
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
            @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "文件类型筛选（可选）", example = "PDF")
            @RequestParam(required = false) String fileType,
            @Parameter(description = "文件名模糊搜索（可选）", example = "二次函数")
            @RequestParam(required = false) String name
    ) {
        Page<FileBO> page = fileService.listDocuments(pageNum, pageSize, fileType, name);
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
}