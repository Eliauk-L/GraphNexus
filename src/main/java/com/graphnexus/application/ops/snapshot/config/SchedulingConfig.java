package com.graphnexus.application.ops.snapshot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度配置。
 * 为运营统计快照采集启用 Spring @Scheduled 支持。
 * 见 ADR-049。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}