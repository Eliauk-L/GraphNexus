package com.graphnexus.api.system.controller;

import com.graphnexus.api.system.dto.LogContentVO;
import com.graphnexus.api.system.dto.LogFileVO;
import com.graphnexus.application.system.service.LogService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 日志查看 REST API 控制器。
 *
 * @author Jay
 * @date 2026/06/23
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "系统运维", description = "系统健康检测 + 日志查看")
@PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER','OPS_STAFF')")
public class LogController {

    private final LogService logService;

    @Operation(summary = "列出所有日志文件", description = "返回 logs/ 目录下所有日志文件的元数据列表，按修改时间倒序")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "文件列表获取成功"),
            @ApiResponse(responseCode = "401", description = "A0025 未登录或 Token 过期"),
            @ApiResponse(responseCode = "403", description = "A0030 权限不足"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/logs")
    public ApiResult<List<LogFileVO>> listFiles() {
        return ApiResult.success(logService.listFiles());
    }

    @Operation(summary = "分页读取日志内容", description = "按文件名 + 分页参数读取日志文件内容，默认 200 行/页，最大 500")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "日志内容获取成功"),
            @ApiResponse(responseCode = "400", description = "A0034 文件名非法"),
            @ApiResponse(responseCode = "401", description = "A0025 未登录或 Token 过期"),
            @ApiResponse(responseCode = "403", description = "A0030 权限不足"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/logs/{filename}")
    public ApiResult<LogContentVO> readContent(
            @Parameter(description = "日志文件名", required = true) @PathVariable String filename,
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页行数，默认 200，最大 500") @RequestParam(defaultValue = "200") int size) {
        return ApiResult.success(logService.readContent(filename, page, size));
    }

    @Operation(summary = "下载日志文件", description = "以附件形式下载指定日志文件")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "文件下载成功"),
            @ApiResponse(responseCode = "400", description = "A0034 文件名非法"),
            @ApiResponse(responseCode = "401", description = "A0025 未登录或 Token 过期"),
            @ApiResponse(responseCode = "403", description = "A0030 权限不足"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/logs/{filename}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @Parameter(description = "日志文件名", required = true) @PathVariable String filename) {

        Path filePath = logService.resolveLogFile(filename);

        if (!Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        StreamingResponseBody body = outputStream -> {
            Files.copy(filePath, outputStream);
            outputStream.flush();
        };

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (Exception e) {
            fileSize = -1;
        }

        var responseBuilder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        if (fileSize >= 0) {
            responseBuilder.contentLength(fileSize);
        }

        return responseBuilder.body(body);
    }
}