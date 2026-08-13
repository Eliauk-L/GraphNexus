package com.graphnexus.api.mastery.controller;

import com.graphnexus.application.mastery.model.MasteryHistoryView;
import com.graphnexus.application.mastery.model.MasteryView;
import com.graphnexus.application.mastery.service.MasteryQueryService;
import com.graphnexus.application.mastery.service.MasteryUpdateService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;

@RestController
@RequestMapping("/api/v1/mastery")
@RequiredArgsConstructor
@Tag(name = "知识点掌握度", description = "查询 EMA 掌握度及其考试证据")
public class MasteryController {
    private final MasteryQueryService queryService;
    private final MasteryUpdateService updateService;

    @GetMapping("/{studentNo}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    @Operation(summary = "查询学生当前知识点掌握度")
    public ApiResult<List<MasteryView>> current(
            @PathVariable String studentNo, @RequestParam String subject,
            Authentication authentication) {
        requireSelfIfStudent(studentNo, authentication);
        return ApiResult.success(queryService.current(studentNo, subject));
    }

    @GetMapping("/{studentNo}/{knowledgePointId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
    @Operation(summary = "查询单知识点掌握度变化历史")
    public ApiResult<List<MasteryHistoryView>> history(
            @PathVariable String studentNo, @PathVariable String knowledgePointId,
            Authentication authentication) {
        requireSelfIfStudent(studentNo, authentication);
        return ApiResult.success(queryService.history(studentNo, knowledgePointId));
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "从考试记录重建学生掌握度")
    public ApiResult<Map<String, Object>> rebuild(
            @RequestParam String studentNo, @RequestParam String subject) {
        int eventCount = updateService.rebuild(studentNo, subject);
        return ApiResult.success(Map.of("studentNo", studentNo, "subject", subject,
                "eventCount", eventCount));
    }

    private void requireSelfIfStudent(String studentNo, Authentication authentication) {
        boolean student = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority()));
        if (student && !authentication.getName().equals(studentNo)) {
            throw new BusinessException(ErrorCode.A0003);
        }
    }
}
