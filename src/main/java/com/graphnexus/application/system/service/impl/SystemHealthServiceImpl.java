package com.graphnexus.application.system.service.impl;

import com.graphnexus.api.system.dto.SystemHealthVO;
import com.graphnexus.application.system.service.SystemHealthService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统健康业务实现。
 *
 * <p>通过 {@link HealthEndpoint} 获取 Actuator 聚合后的全量健康数据
 * （含通过 {@code HealthContributor} 体系注册的 DataSource/Neo4j），
 * 过滤仅保留目标4组件；同时从 {@link MeterRegistry} 采集 JVM 运行时指标。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthServiceImpl implements SystemHealthService {

    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;

    /** 目标组件：Actuator 组件 key → 页面显示名 */
    private static final Map<String, String> TARGET_COMPONENTS = new LinkedHashMap<>();
    static {
        TARGET_COMPONENTS.put("db", "MySQL");
        TARGET_COMPONENTS.put("neo4j", "Neo4j");
        TARGET_COMPONENTS.put("redis", "Redis");
        TARGET_COMPONENTS.put("minio", "MinIO");
    }

    @Override
    public SystemHealthVO getSystemHealth() {
        List<SystemHealthVO.ComponentHealth> components = new ArrayList<>();

        var health = healthEndpoint.health();
        Map<String, HealthComponent> allComponents = health.getComponents();
        log.debug("Actuator 健康组件 keys: {}", allComponents.keySet());

        for (var entry : TARGET_COMPONENTS.entrySet()) {
            String key = entry.getKey();
            String displayName = entry.getValue();

            // 忽略大小写匹配（MinIOHealthIndicator → minIO key）
            HealthComponent component = null;
            for (var e : allComponents.entrySet()) {
                if (e.getKey().equalsIgnoreCase(key)) {
                    component = e.getValue();
                    break;
                }
            }
            if (component == null) {
                log.debug("健康组件 {} 不存在于 Actuator 聚合结果中", key);
                continue;
            }

            try {
                var builder = SystemHealthVO.ComponentHealth.builder()
                        .name(displayName)
                        .status(component.getStatus().getCode());

                if (component instanceof Health h && h.getDetails() != null) {
                    Object latencyObj = h.getDetails().get("latency");
                    if (latencyObj instanceof Long latency) {
                        builder.latency(latency);
                    }
                    Object errorObj = h.getDetails().get("error");
                    if (errorObj != null) {
                        builder.error(errorObj.toString());
                    }
                }
                components.add(builder.build());
            } catch (Exception e) {
                log.warn("{} 健康检查异常: {}", displayName, e.getMessage());
                components.add(SystemHealthVO.ComponentHealth.builder()
                        .name(displayName)
                        .status("DOWN")
                        .error(e.getMessage())
                        .build());
            }
        }

        return SystemHealthVO.builder()
                .components(components)
                .jvm(buildJvmMetrics())
                .build();
    }

    private SystemHealthVO.JvmMetrics buildJvmMetrics() {
        return SystemHealthVO.JvmMetrics.builder()
                .heapUsed(safeGaugeValue("jvm.memory.used"))
                .heapMax(safeGaugeValue("jvm.memory.max"))
                .cpuUsage(safeGaugeDouble("system.cpu.usage"))
                .threadCount((int) safeGaugeValue("jvm.threads.live"))
                .gcCount(safeTimerCount("jvm.gc.pause"))
                .build();
    }

    private long safeGaugeValue(String name) {
        try {
            var gauge = meterRegistry.get(name).gauge();
            return gauge != null ? (long) gauge.value() : 0;
        } catch (Exception e) {
            log.debug("获取指标 {} 失败: {}", name, e.getMessage());
            return 0;
        }
    }

    private double safeGaugeDouble(String name) {
        try {
            var gauge = meterRegistry.get(name).gauge();
            return gauge != null ? gauge.value() : 0.0;
        } catch (Exception e) {
            log.debug("获取指标 {} 失败: {}", name, e.getMessage());
            return 0.0;
        }
    }

    private long safeTimerCount(String name) {
        try {
            var timer = meterRegistry.get(name).timer();
            return timer != null ? timer.count() : 0;
        } catch (Exception e) {
            log.debug("获取Timer指标 {} 失败: {}", name, e.getMessage());
            return 0;
        }
    }
}