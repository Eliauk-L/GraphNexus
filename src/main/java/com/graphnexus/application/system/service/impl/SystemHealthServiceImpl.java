package com.graphnexus.application.system.service.impl;

import com.graphnexus.api.system.dto.SystemHealthVO;
import com.graphnexus.application.system.service.SystemHealthService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统健康业务实现。
 *
 * <p>注入 Spring 自动收集的所有 {@link HealthIndicator} Bean，
 * 逐个调用聚合为统一 VO；同时从 {@link MeterRegistry} 采集 JVM 运行时指标。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthServiceImpl implements SystemHealthService {

    private final List<HealthIndicator> healthIndicators;
    private final MeterRegistry meterRegistry;

    @Override
    public SystemHealthVO getSystemHealth() {
        List<SystemHealthVO.ComponentHealth> components = new ArrayList<>();

        for (HealthIndicator indicator : healthIndicators) {
            String name = indicator.getClass().getSimpleName().replace("HealthIndicator", "");
            try {
                var health = indicator.health();
                var builder = SystemHealthVO.ComponentHealth.builder()
                        .name(name)
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
                log.warn("{} 健康检查异常: {}", name, e.getMessage());
                components.add(SystemHealthVO.ComponentHealth.builder()
                        .name(name)
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