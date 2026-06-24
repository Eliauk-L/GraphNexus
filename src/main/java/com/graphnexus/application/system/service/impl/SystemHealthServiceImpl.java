package com.graphnexus.application.system.service.impl;

import com.graphnexus.api.system.dto.SystemHealthVO;
import com.graphnexus.application.system.service.SystemHealthService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统健康业务实现。
 *
 * <p>通过 {@link HealthContributorRegistry} 获取所有已注册的健康组件
 * （含 DataSource、Neo4j 等通过 {@code HealthContributor} 注册的内置组件），
 * 过滤仅保留目标4组件；同时从 {@link MeterRegistry} 采集 JVM 运行时指标。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
public class SystemHealthServiceImpl implements SystemHealthService {

    private final HealthContributorRegistry healthContributorRegistry;
    private final MeterRegistry meterRegistry;

    /** 目标组件：registry key → 页面显示名 */
    private static final Map<String, String> TARGET_COMPONENTS = new LinkedHashMap<>();
    static {
        TARGET_COMPONENTS.put("db", "MySQL");
        TARGET_COMPONENTS.put("neo4j", "Neo4j");
        TARGET_COMPONENTS.put("redis", "Redis");
        TARGET_COMPONENTS.put("minio", "MinIO");
    }

    public SystemHealthServiceImpl(HealthContributorRegistry healthContributorRegistry,
                                   MeterRegistry meterRegistry) {
        this.healthContributorRegistry = healthContributorRegistry;
        this.meterRegistry = meterRegistry;
        if (log.isDebugEnabled()) {
            List<String> keys = new ArrayList<>();
            for (var nc : healthContributorRegistry) {
                keys.add(nc.getName());
            }
            log.debug("已注册的健康组件: {}", keys);
        }
    }

    @Override
    public SystemHealthVO getSystemHealth() {
        List<SystemHealthVO.ComponentHealth> components = new ArrayList<>();

        for (Map.Entry<String, String> entry : TARGET_COMPONENTS.entrySet()) {
            String key = entry.getKey();
            String displayName = entry.getValue();

            HealthContributor contributor = findContributor(key);
            if (contributor == null) {
                log.debug("健康组件 '{}' 未找到", key);
                continue;
            }

            if (!(contributor instanceof HealthIndicator indicator)) {
                log.debug("健康组件 '{}' 不是 HealthIndicator，跳过", key);
                continue;
            }

            try {
                var health = indicator.health();
                var builder = SystemHealthVO.ComponentHealth.builder()
                        .name(displayName)
                        .status(health.getStatus().getCode());

                if (health.getDetails() != null) {
                    Object latencyObj = health.getDetails().get("latency");
                    if (latencyObj instanceof Long latency) {
                        builder.latency(latency);
                    }
                    Object errorObj = health.getDetails().get("error");
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

    /**
     * 忽略大小写查找 HealthContributor。
     */
    private HealthContributor findContributor(String key) {
        for (var nc : healthContributorRegistry) {
            if (nc.getName().equalsIgnoreCase(key)) {
                return nc.getContributor();
            }
        }
        return null;
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