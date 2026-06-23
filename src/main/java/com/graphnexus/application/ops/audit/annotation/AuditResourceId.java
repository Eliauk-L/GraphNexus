package com.graphnexus.application.ops.audit.annotation;

import java.lang.annotation.*;

/**
 * 标记方法参数为审计日志的关联资源 ID。
 *
 * <p>与 {@link Auditable} 配合使用。被标注的参数值将作为
 * {@code audit_log.resource_id} 写入。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * @Auditable(OperationType.DOCUMENT_UPLOAD)
 * public TextbookBO upload(MultipartFile file,
 *                          @AuditResourceId String documentId) { ... }
 * }</pre>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditResourceId {
}