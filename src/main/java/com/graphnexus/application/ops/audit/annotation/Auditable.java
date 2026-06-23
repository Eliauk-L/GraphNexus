package com.graphnexus.application.ops.audit.annotation;

import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;

import java.lang.annotation.*;

/**
 * 标记需要自动记录操作审计日志的方法。
 *
 * <p>被标注的方法执行成功后，由 {@code AuditAspect} 自动提取
 * {@code userId}（SecurityContext）+ {@code resourceId}
 * （{@link AuditResourceId} 注解的参数），异步写入审计日志。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * @Auditable(OperationType.LOGIN)
 * public LoginResponse login(LoginRequest request) { ... }
 *
 * @Auditable(OperationType.DOCUMENT_UPLOAD)
 * public TextbookBO upload(MultipartFile file, String subject) { ... }
 * }</pre>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /** 操作类型 */
    OperationType value();
}