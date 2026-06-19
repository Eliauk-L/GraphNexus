package com.graphnexus.api.file.controller;

import com.graphnexus.api.file.dto.textbook.TextbookVO;
import com.graphnexus.api.file.dto.textbook.TextbookParseResultVO;
import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.parse.ParseResult;
import com.graphnexus.application.file.textbook.service.TextbookService;
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

/**
 * 教材处理 REST API 控制器 — 仅教材文件（PDF/TXT）。
 *
 * <p>上传仅存储文件 + 入库（返回 filePath），解析走 POST /parse/{id}，
 * 抽取走 POST /api/v1/graph/extract/{id}，融合走 POST /api/v1/graph/fusion/execute。
 * CSV 成绩上传走独立的 {@link com.graphnexus.api.file.controller.GradeController}。</p>
 *
 * @author Jay
 * @date 2026/06/12
 */
@RestController
@RequestMapping("/api/v1/file/textbooks")
@RequiredArgsConstructor
@Tag(name = "教材处理", description = "教材上传解析管理（PDF/TXT）")
public class TextbookController {

    private final TextbookService textBookService;

    /**
     * 上传教材文件（PDF/TXT）— 仅存储 + 入库，不做后续处理。
     *
     * <p>返回的 TextbookVO 含 id + filePath，状态为 UPLOADED。
     * 后续调用 POST /parse/{id} 触发文本解析。</p>
     */
    @Operation(summary = "上传教材", description = "上传 PDF 或 TXT 教材文件，仅存储入库（状态 UPLOADED）。后续需调用 /parse/{id} 触发文本解析")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上传成功（status=UPLOADED）",
                    content = {@Content(mediaType = "application/json", schema = @Schema(implementation = TextbookVO.class))}),
            @ApiResponse(responseCode = "400", description = "A0004 文件类型不支持"),
            @ApiResponse(responseCode = "409", description = "A0007 该学科下已存在相同内容教材"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/upload")
    public ApiResult<TextbookVO> upload(
            @Parameter(description = "上传文件（支持 PDF/TXT）", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "学科名称", required = true, example = "数学")
            @RequestParam("subject") String subject
    ) {
        TextbookBO bo = textBookService.upload(file, subject);
        return ApiResult.success(TextbookVO.from(bo));
    }

    /**
     * 触发教材解析（前端主动调用）。
     *
     * <p>从 MinIO 读取文件 → 文本提取 → 入库。解析完成后状态变为 PARSED。
     * 后续抽取走 {@code POST /api/v1/graph/extract/{id}}，融合走 {@code POST /api/v1/graph/fusion/execute}。</p>
     */
    @Operation(summary = "解析教材文本", description = "对已上传的教材执行文本解析（MinerU 优先，PDFBox 兜底），提取文本内容并入库")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解析完成，返回文本内容与页数"),
            @ApiResponse(responseCode = "400", description = "A0004 解析失败 / A0009 状态不允许解析"),
            @ApiResponse(responseCode = "404", description = "A0006 教材不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/parse/{id}")
    public ApiResult<TextbookParseResultVO> parse(
            @PathVariable @Parameter(description = "教材 ID", required = true, example = "1") Long id) {
        ParseResult result = textBookService.parse(id);
        return ApiResult.success(TextbookParseResultVO.from(id, result));
    }

    /**
     * 分页查询教材列表。
     */
    @Operation(summary = "分页查询教材列表", description = "按上传时间倒序分页返回教材列表。页码从 1 开始")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分页教材列表"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping
    public ApiResult<PageResult<TextbookVO>> list(
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页大小", example = "10")
            @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "文件类型筛选（可选）", example = "PDF")
            @RequestParam(required = false) String fileType,
            @Parameter(description = "文件名模糊搜索（可选）", example = "二次函数")
            @RequestParam(required = false) String name
    ) {
        Page<TextbookBO> page = textBookService.listTextBooks(pageNum, pageSize, fileType, name);
        return ApiResult.success(PageResult.of(page.map(TextbookVO::from)));
    }

    /**
     * 查询单个教材。
     */
    @Operation(summary = "查询教材详情", description = "按教材 ID 查询单条教材的完整元数据（含文件大小、页数、状态等）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "教材详情"),
            @ApiResponse(responseCode = "404", description = "A0006 教材不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/{id}")
    public ApiResult<TextbookVO> get(
            @Parameter(description = "教材 ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        TextbookBO bo = textBookService.getTextBook(id);
        return ApiResult.success(TextbookVO.from(bo));
    }

    /**
     * 删除教材（逻辑删除 + MinIO 物理清除）。
     */
    @Operation(summary = "删除教材", description = "级联删除：标记 DELETING 中间状态 → 删除 MinIO 文件 → 清除 Neo4j 关联子图（EntityNode + 边） → 标记 DELETED 终态。保留共享 KnowledgePoint 节点不级联。幂等操作，重复删除返回成功")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功（data=null）"),
            @ApiResponse(responseCode = "404", description = "A0006 教材不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "教材 ID", required = true, example = "1")
            @PathVariable("id") Long id) {
        textBookService.deleteTextBook(id);
        return ApiResult.success(null);
    }
}