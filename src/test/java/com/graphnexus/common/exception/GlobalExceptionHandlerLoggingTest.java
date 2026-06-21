package com.graphnexus.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlobalExceptionHandler 异常日志行为单元测试（exception-traceability）。
 *
 * <p>纯 JUnit 5——{@code new GlobalExceptionHandler()} 直调，无 Spring 上下文。
 * 用 logback {@link ListAppender} 挂到 {@link GlobalExceptionHandler} 的 logger 捕获日志事件，
 * 验证兜底异常补全的 ERROR 完整堆栈日志 + traceId 关联（AC-1）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("GlobalExceptionHandler 异常日志测试")
class GlobalExceptionHandlerLoggingTest {

    /** 固定 traceId，用于断言日志 MDC traceId == 响应体 traceId（AC-1）。 */
    private static final String FIXED_TRACE_ID = "fixed-trace-id-ac1";

    private GlobalExceptionHandler handler;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        MDC.put("traceId", FIXED_TRACE_ID);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    /**
     * AC-1 · 兜底未捕获异常——完整堆栈按 traceId 落日志。
     *
     * <p>验证方式取自 REQUIREMENT AC-1：ListAppender 捕获日志，断言含
     * {@code RuntimeException}、{@code bean X not found}、cause 链与堆栈帧（行号），
     * 且 MDC {@code traceId} == 响应体 {@code traceId}。</p>
     */
    @Test
    @DisplayName("兜底异常打印完整堆栈")
    void 兜底异常打印完整堆栈() {
        // 带原因链的运行期异常——模拟未被任何层 wrap 的 raw Exception，直达兜底
        Exception ex = new RuntimeException("bean X not found",
                new IllegalStateException("root cause"));

        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(ex);

        // 断言①：响应 HTTP 500 + ErrorResponse(B0001, 类名级 errorMessage, B0001 tip, 固定 traceId, timestamp)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body, "响应体非空");
        assertEquals(ErrorCode.B0001.getErrorCode(), body.errorCode());
        assertEquals("系统内部异常: RuntimeException", body.errorMessage());
        assertEquals(ErrorCode.B0001.getDefaultUserTip(), body.userTip());
        assertEquals(FIXED_TRACE_ID, body.traceId());
        assertNotNull(body.timestamp());

        // 断言②：捕获到 ERROR 事件，格式化输出含异常类名 + 异常 message + cause 链 + 堆栈帧（行号）
        ILoggingEvent errorEvent = appender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow(() -> new AssertionError("应捕获到 ERROR 级日志事件（兜底异常须落 ERROR 完整堆栈）"));
        String rendered = renderWithThrowable(errorEvent);
        assertTrue(rendered.contains("RuntimeException"), "应含异常类名: " + rendered);
        assertTrue(rendered.contains("bean X not found"), "应含异常 message: " + rendered);
        assertTrue(rendered.contains("Caused by:"), "应含 cause 链渲染: " + rendered);
        assertTrue(rendered.contains("IllegalStateException"), "应含 cause 异常类名: " + rendered);
        assertTrue(rendered.contains("root cause"), "应含 cause message: " + rendered);
        assertTrue(rendered.contains("GlobalExceptionHandlerLoggingTest"),
                "应含堆栈帧（本测试类）: " + rendered);
        assertTrue(rendered.contains(".java:"), "堆栈帧应含行号（debug 信息保留）: " + rendered);

        // 断言③：该 ERROR 事件 MDC traceId == 响应体 traceId（AC-1 核心）
        String mdcTraceId = errorEvent.getMDCPropertyMap().get("traceId");
        assertNotNull(mdcTraceId, "日志事件应携带 traceId");
        assertEquals(body.traceId(), mdcTraceId, "日志 MDC traceId 应 == 响应体 traceId");
    }

    /**
     * 用 logback PatternLayout 把事件渲染为「message + 完整堆栈」字符串，
     * 等价于该事件落入 {@code graphnexus-error.log} 的内容形态。
     */
    private String renderWithThrowable(ILoggingEvent event) {
        LoggerContext context = logger.getLoggerContext();
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("%msg%n%ex");
        layout.start();
        try {
            return layout.doLayout(event);
        } finally {
            layout.stop();
        }
    }
}
