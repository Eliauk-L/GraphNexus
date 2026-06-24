package com.graphnexus.api.query.controller;

import com.graphnexus.api.query.dto.chat.*;
import com.graphnexus.api.query.dto.history.HistoryQueryRequest;
import com.graphnexus.api.query.dto.history.HistoryRecordVO;
import com.graphnexus.application.query.chat.model.QueryResultBO;
import com.graphnexus.application.query.chat.service.QueryService;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.PageResult;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskDO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 智能问答 REST API 控制器。
 *
 * @author Jay
 * @date 2026/06/17
 */
@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@Tag(name = "智能问答", description = "自然语言问答 — 图剪枝驱动 LLM 分析诊断")
public class QueryController {

    private final QueryService queryService;

    /**
     * 同步问答。
     */
    @Operation(summary = "同步问答", description = "提交问题后同步等待 LLM 分析结果（≤ 30s 超时）。需显式指定 studentName/studentNo/subject 参数。系统执行：意图识别(STUDENT_DIAGNOSIS) → 图剪枝(Student Diagnosis Strategy) → LLM 分析生成 → 返回 Markdown 结论")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分析完成，返回 Markdown 格式结论 + token 用量"),
            @ApiResponse(responseCode = "400", description = "A0002 参数校验失败 / A0019 无法识别查询意图"),
            @ApiResponse(responseCode = "409", description = "A0020 存在多个同名 Student，需使用学号精确指定"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / C0001 LLM API 调用失败")
    })
    @PostMapping("/ask")
    public ApiResult<QueryAskResponse> ask(@RequestBody @Valid QueryAskRequest request) {
        QueryResultBO result = queryService.ask(
                request.question(), request.studentName(),
                request.studentNo(), request.subject());
        return ApiResult.success(QueryAskResponse.from(result));
    }

    /**
     * 异步问答 — 立即返回 taskId。
     */
    @Operation(summary = "异步问答", description = "提交复杂问题后立即返回 taskId（状态 PENDING），后台异步执行图剪枝 + LLM 分析。通过 GET /result/{taskId} 轮询获取结果。适用于耗时可能超过 30s 的复杂查询")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "任务已提交，返回 taskId 供轮询"),
            @ApiResponse(responseCode = "400", description = "A0002 参数校验失败 / A0019 无法识别查询意图"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/ask-async")
    public ApiResult<QueryAsyncResponse> askAsync(@RequestBody @Valid QueryAskRequest request) {
        String taskId = queryService.askAsync(
                request.question(), request.studentName(),
                request.studentNo(), request.subject());
        return ApiResult.success(new QueryAsyncResponse(taskId, "PENDING", LocalDateTime.now()));
    }

    /**
     * 智能对话 — 接受原始自然语言问题，自动提取学生姓名、学科等信息后执行诊断。
     */
    @Operation(summary = "智能对话", description = "仅需提供自然语言问题，系统自动提取实体（LLM-first 实体提取 + 正则 fallback）：studentName/studentNo/subject，然后执行诊断分析。比 /ask 更智能但稍慢（多一次 LLM 实体提取调用）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分析完成"),
            @ApiResponse(responseCode = "400", description = "A0002 问题不能为空 / A0019 无法识别查询意图"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / C0001 LLM API 调用失败")
    })
    @PostMapping("/chat")
    public ApiResult<QueryAskResponse> chat(@RequestBody @Valid QueryChatRequest request) {
        QueryResultBO result = queryService.chat(request.question());
        return ApiResult.success(QueryAskResponse.from(result));
    }

    /**
     * 查询异步问答结果。
     */
    @Operation(summary = "查询异步结果", description = "轮询异步问答任务的执行状态和结果。status 枚举：PENDING（排队中）→ PROCESSING（执行中）→ COMPLETED（完成，answer 有值）或 FAILED（失败，errorMessage 有值）。前端建议轮询间隔 1-2 秒")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "任务当前状态和结果"),
            @ApiResponse(responseCode = "404", description = "A0021 任务不存在或已过期"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/result/{taskId}")
    public ApiResult<QueryResultResponse> getResult(
            @Parameter(description = "任务 ID（UUID 格式）", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String taskId) {
        QueryResultBO result = queryService.getResult(taskId);
        return ApiResult.success(QueryResultResponse.from(result));
    }

    // ======================== 历史查询与导出 ========================

    /**
     * 历史诊断记录分页查询。
     */
    @Operation(summary = "历史诊断记录查询", description = "分页查询历史诊断记录，支持按学生姓名/学号/学科/状态/时间范围筛选。所有筛选参数可选。列表不含 answer 正文，详情通过 GET /result/{taskId} 按需加载。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "分页查询结果"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/history")
    public ApiResult<PageResult<HistoryRecordVO>> queryHistory(@Valid HistoryQueryRequest req) {
        PageResult<HistoryRecordVO> result = queryService.queryHistory(req);
        return ApiResult.success(result);
    }

    /**
     * 导出单条诊断报告（HTML 格式）。
     */
    @Operation(summary = "导出单条诊断报告", description = "下载单条诊断的完整 LLM 分析报告为 HTML 文件。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "HTML 文件下载"),
            @ApiResponse(responseCode = "404", description = "A0021 任务不存在"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/history/{taskId}/export")
    public ResponseEntity<byte[]> exportSingle(
            @Parameter(description = "任务 ID（UUID 格式）", required = true)
            @PathVariable String taskId) {

        QueryTaskDO task = queryService.exportSingle(taskId);
        byte[] answerBytes = (task.getAnswer() != null ? task.getAnswer() : "").getBytes(StandardCharsets.UTF_8);

        String shortId = taskId.length() > 8 ? taskId.substring(0, 8) : taskId;
        String filename = "diagnosis-" + shortId + ".html";

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/html; charset=UTF-8"))
                .contentLength(answerBytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(answerBytes);
    }

    /**
     * 删除单条历史诊断记录。
     */
    @Operation(summary = "删除历史诊断记录", description = "物理删除单条历史诊断记录（query_task 为日志表，不做逻辑删除）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "A0021 任务不存在"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @DeleteMapping("/history/{taskId}")
    public ApiResult<String> deleteHistory(
            @Parameter(description = "任务 ID（UUID 格式）", required = true)
            @PathVariable String taskId) {
        queryService.deleteHistory(taskId);
        return ApiResult.success("ok");
    }
}