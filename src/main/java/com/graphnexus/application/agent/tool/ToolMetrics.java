package com.graphnexus.application.agent.tool;

/** 单次 Tool 调用指标。 */
public record ToolMetrics(long elapsedMs, int resultCount, boolean truncated) {}
