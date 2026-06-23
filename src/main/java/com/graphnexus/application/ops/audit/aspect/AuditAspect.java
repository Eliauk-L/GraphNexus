package com.graphnexus.application.ops.audit.aspect;

import com.graphnexus.application.ops.audit.annotation.AuditResourceId;
import com.graphnexus.application.ops.audit.annotation.Auditable;
import com.graphnexus.application.ops.audit.service.AuditLogService;
import com.graphnexus.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

/**
 * 操作审计 AOP 切面。拦截 {@link Auditable} 方法，
 * 执行成功后自动异步写入审计日志。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>从 {@code SecurityContextHolder} 提取当前用户 ID</li>
 *   <li>从方法参数中查找 {@link AuditResourceId} 注解的参数作为 resourceId</li>
 *   <li>调用 {@link AuditLogService#record} 异步写入（不阻塞主流程）</li>
 *   <li>切面内异常不影响主方法返回值</li>
 * </ol>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // 执行业务方法
        Object result = joinPoint.proceed();

        // 异步记录审计日志（不阻塞，异常不抛出）
        try {
            Long userId = getCurrentUserId();
            String resourceId = extractResourceId(joinPoint);
            auditLogService.record(userId, auditable.value(), resourceId);
        } catch (Exception e) {
            log.warn("AOP审计日志写入失败: method={}, reason={}",
                    joinPoint.getSignature().toShortString(), e.getMessage());
        }

        return result;
    }

    /** 从 SecurityContext 获取当前用户 ID */
    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return 0L;
    }

    /** 从方法参数中提取 @AuditResourceId 注解的参数值 */
    private String extractResourceId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            for (Annotation annotation : parameters[i].getAnnotations()) {
                if (annotation instanceof AuditResourceId) {
                    Object arg = args[i];
                    return arg != null ? arg.toString() : null;
                }
            }
        }
        return null;
    }
}