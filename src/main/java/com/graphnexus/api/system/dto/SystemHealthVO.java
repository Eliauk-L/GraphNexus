package com.graphnexus.api.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 系统健康响应 VO。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "系统健康状态")
public class SystemHealthVO {

    @Schema(description = "各组件健康状态列表")
    private List<ComponentHealth> components;

    @Schema(description = "JVM 运行时指标")
    private JvmMetrics jvm;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "组件健康状态")
    public static class ComponentHealth {
        @Schema(description = "组件名称", example = "MySQL")
        private String name;

        @Schema(description = "状态：UP 或 DOWN", example = "UP")
        private String status;

        @Schema(description = "响应延迟（毫秒）")
        private Long latency;

        @Schema(description = "错误原因（DOWN 时填充）")
        private String error;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "JVM 运行时指标")
    public static class JvmMetrics {
        @Schema(description = "堆内存已使用量（字节）")
        private Long heapUsed;

        @Schema(description = "堆内存最大可用量（字节）")
        private Long heapMax;

        @Schema(description = "CPU 使用率（0.0~1.0）")
        private Double cpuUsage;

        @Schema(description = "活跃线程数")
        private Integer threadCount;

        @Schema(description = "GC 总次数")
        private Long gcCount;
    }
}