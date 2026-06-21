package com.graphnexus.common.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlobalExceptionHandler 异常日志行为单元测试（exception-traceability）。
 *
 * <p>纯 JUnit 5——{@code new GlobalExceptionHandler()} 直调，无 Spring 上下文。
 * 用 logback {@link ListAppender} 挂到 {@link GlobalExceptionHandler} 的 logger 捕获日志事件，
 * 验证兜底异常补全的 ERROR 完整堆栈日志 + traceId 关联（AC-1）、
 * 预期异常分级 WARN 日志（AC-2）、响应体字段回归（AC-3）。</p>
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

    // ======================== T02 测试方法 ========================

    /**
     * AC-2 · 业务/校验/权限异常分级落 WARN 日志，不产生 ERROR 完整堆栈条目。
     *
     * <p>验证方式取自 REQUIREMENT AC-2：分别调三个预期异常 handler，
     * 断言各自落 WARN 级事件、无 ERROR 级完整堆栈条目、且 WARN 事件不携带 throwable
     * （即不渲染堆栈——WARN 经 ThresholdFilter(ERROR) 不入 graphnexus-error.log 的语义体现）。</p>
     */
    @Test
    @DisplayName("业务校验权限异常分级落日志")
    void 业务校验权限异常分级落日志() throws Exception {
        // Call 3 expected-exception handlers
        handler.handleBusinessException(new BusinessException(ErrorCode.A0001));

        Method method = GlobalExceptionHandler.class.getMethod(
                "handleValidationException", MethodArgumentNotValidException.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        BindException bindException = new BindException(new Object(), "target");
        // addError 无需访问 bean property，避免 NotReadablePropertyException
        bindException.addError(new FieldError("target", "name", "姓名不能为空"));
        bindException.addError(new FieldError("target", "email", "邮箱格式不正确"));
        MethodArgumentNotValidException validationEx =
                new MethodArgumentNotValidException(methodParameter, bindException.getBindingResult());
        handler.handleValidationException(validationEx);

        handler.handleAccessDeniedException(new AccessDeniedException("Access is denied"));

        // 断言①：捕获到 3 条 WARN 事件、0 ERROR 事件
        var warnEvents = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .collect(Collectors.toList());
        assertEquals(3, warnEvents.size(), "应有 3 条 WARN 事件");

        boolean hasError = appender.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR);
        assertFalse(hasError, "不应有 ERROR 级事件（AC-2：预期异常不污染 error 日志）");

        // 断言②：每条 WARN 不携带 throwable（无堆栈渲染）
        for (ILoggingEvent event : warnEvents) {
            assertTrue(event.getThrowableProxy() == null,
                    "WARN 事件不应携带 throwable: " + event.getFormattedMessage());
        }

        // 断言③：WARN 消息内容——业务异常含 errorCode + message
        assertTrue(warnEvents.get(0).getFormattedMessage().contains("业务异常"),
                "第 1 条应为业务异常 WARN");
        assertTrue(warnEvents.get(0).getFormattedMessage().contains(ErrorCode.A0001.getErrorCode()),
                "应含 errorCode A0001");
        assertTrue(warnEvents.get(0).getFormattedMessage().contains(
                String.valueOf(ErrorCode.A0001.getHttpStatusCode())),
                "应含 httpStatus 404");

        // 断言④：校验异常含 fieldErrors 拼接
        assertTrue(warnEvents.get(1).getFormattedMessage().contains("参数校验失败"),
                "第 2 条应为参数校验失败 WARN");
        assertTrue(warnEvents.get(1).getFormattedMessage().contains("name:"),
                "应含字段 name 的校验信息");
        assertTrue(warnEvents.get(1).getFormattedMessage().contains("email:"),
                "应含字段 email 的校验信息");
        assertTrue(warnEvents.get(1).getFormattedMessage().contains("姓名不能为空"),
                "应含字段描述");

        // 断言⑤：权限异常含 errorCode + access message
        assertTrue(warnEvents.get(2).getFormattedMessage().contains("权限不足"),
                "第 3 条应为权限不足 WARN");
        assertTrue(warnEvents.get(2).getFormattedMessage().contains(ErrorCode.A0003.getErrorCode()),
                "应含 errorCode A0003");
        assertTrue(warnEvents.get(2).getFormattedMessage().contains("Access is denied"),
                "应含原始权限异常消息");
    }

    /**
     * AC-3 · 前端响应体逐字段不变——回归无破坏。
     *
     * <p>用 Jackson {@link ObjectMapper} 序列化兜底路径的 ErrorResponse 为 JSON，
     * 断言字段集 == {errorCode, errorMessage, userTip, traceId, timestamp} 共 5 字段，
     * 且 errorMessage 不含异常 message 原文（安全边界——内部细节只走日志不回前端）。</p>
     */
    @Test
    @DisplayName("响应体字段不变")
    void 响应体字段不变() throws Exception {
        // 兜底异常——模拟未被任何层 wrap 的 raw Exception
        Exception ex = new RuntimeException("secret database connection refused: db.internal:5432");

        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(ex);
        ErrorResponse body = response.getBody();
        assertNotNull(body, "响应体非空");

        // 序列化为 JSON 并解析树结构
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String json = mapper.writeValueAsString(body);
        JsonNode root = mapper.readTree(json);

        // 断言①：字段集合 == {errorCode, errorMessage, userTip, traceId, timestamp} 共 5 字段
        Set<String> fieldNames = new HashSet<>();
        root.fieldNames().forEachRemaining(fieldNames::add);
        assertEquals(Set.of("errorCode", "errorMessage", "userTip", "traceId", "timestamp"),
                fieldNames, "响应体字段集应为 5 固定字段，无新增/删除字段");

        // 断言②：errorMessage 仍为类名级，不含异常 message 原文（安全边界）
        assertEquals("系统内部异常: RuntimeException", root.get("errorMessage").asText(),
                "errorMessage 应为类名级，不变");
        assertFalse(root.get("errorMessage").asText().contains("secret database connection refused"),
                "errorMessage 不得含异常 getMessage() 原文（内部细节只走日志）");
        assertFalse(root.get("errorMessage").asText().contains("db.internal"),
                "errorMessage 不得含内部主机信息");

        // 断言③：无堆栈/异常 message/根因字段泄露到响应体
        String jsonStr = root.toString();
        assertFalse(jsonStr.contains("stackTrace") || jsonStr.contains("stack_trace"),
                "响应体不得含 stackTrace 字段");
        assertFalse(jsonStr.contains("cause") && !jsonStr.contains("because"),
                "响应体不得含 cause 字段");
        assertFalse(jsonStr.contains("suppressed"), "响应体不得含 suppressed 字段");

        // 断言④：traceId 非空（MDC 由 setUp 注入）
        assertNotNull(root.get("traceId").asText(), "traceId 非空");
        assertFalse(root.get("traceId").asText().isEmpty(), "traceId 不为空字符串");
    }

    // ======================== 辅助方法 ========================

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