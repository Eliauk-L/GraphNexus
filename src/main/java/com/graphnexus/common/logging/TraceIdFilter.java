package com.graphnexus.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 Trace ID 过滤器。
 *
 * <p>在每个 HTTP 请求入口处生成或提取 Trace ID，注入 SLF4J MDC，
 * 使同一请求链路上的所有日志自动携带相同 traceId。</p>
 *
 * <p>规则：
 * <ul>
 *   <li>请求头含 {@code X-Trace-Id} → 沿用客户端传入的值</li>
 *   <li>请求头不含 → 自动生成 {@link UUID}</li>
 *   <li>请求处理完成后在 {@code finally} 中清理 MDC，避免线程池复用污染</li>
 * </ul></p>
 *
 * @author GraphNexus
 * @date 2026/06/11
 */
@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /** 请求头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC 中的 key */
    static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, traceId);

        log.debug("{} {} — traceId: {}", request.getMethod(), request.getRequestURI(), traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}