package com.graphnexus.api.query.controller;

import com.graphnexus.api.query.dto.*;
import com.graphnexus.application.query.model.QueryResultBO;
import com.graphnexus.application.query.service.QueryService;
import com.graphnexus.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
public class QueryController {

    private final QueryService queryService;

    /**
     * 同步问答。
     */
    @PostMapping("/ask")
    public ApiResponse<QueryAskResponse> ask(@RequestBody @Valid QueryAskRequest request) {
        QueryResultBO result = queryService.ask(
                request.question(), request.studentName(),
                request.studentNo(), request.subject());
        return ApiResponse.success(QueryAskResponse.from(result));
    }

    /**
     * 异步问答 — 立即返回 taskId。
     */
    @PostMapping("/ask-async")
    public ApiResponse<QueryAsyncResponse> askAsync(@RequestBody @Valid QueryAskRequest request) {
        String taskId = queryService.askAsync(
                request.question(), request.studentName(),
                request.studentNo(), request.subject());
        return ApiResponse.success(new QueryAsyncResponse(taskId, "PENDING", LocalDateTime.now()));
    }

    /**
     * 查询异步问答结果。
     */
    @GetMapping("/result/{taskId}")
    public ApiResponse<QueryResultResponse> getResult(@PathVariable String taskId) {
        QueryResultBO result = queryService.getResult(taskId);
        return ApiResponse.success(QueryResultResponse.from(result));
    }
}