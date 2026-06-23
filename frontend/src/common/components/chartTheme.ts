/**
 * ECharts 主题配置 —— 运营仪表盘图表默认样式。
 * 按 UI-DESIGN §3 图表色板 + §6 ECharts 容器规约。
 * 色值从 tokens.css 派生（硬编码保持一致性，不依赖 getComputedStyle）。
 */

/** 图表 6 色色板，与 tokens.css --chart-c0~c5 一致 */
export const CHART_COLORS = [
  'oklch(0.55 0.18 250)',   // c0 brand
  'oklch(0.58 0.15 198)',   // c1 teal
  'oklch(0.58 0.15 146)',   // c2 green
  'oklch(0.62 0.15 94)',    // c3 amber
  'oklch(0.58 0.18 42)',    // c4 coral
  'oklch(0.52 0.14 290)',   // c5 violet
]

/** 中性色：图表轴线、tooltip 文字、饼图"其他"切片 */
const CHART_AXIS = 'oklch(0.88 0.005 95)'
const CHART_TOOLTIP_BG = 'oklch(1 0 0)'
const CHART_TEXT_SECONDARY = 'oklch(0.45 0.005 95)'
const CHART_BORDER = 'oklch(0.90 0.005 95)'

/** 获取 ECharts 默认全局 option 覆盖。
 *  在每个图表实例 init 后调用 setOption(defaultOption) 或 merge。
 */
export function getDefaultChartOption() {
  return {
    color: CHART_COLORS,
    animationDuration: 300,
    animationEasing: 'cubicOut' as const,
    grid: {
      top: 16,
      right: 16,
      bottom: 32,
      left: 48,
      containLabel: false,
    },
    tooltip: {
      backgroundColor: CHART_TOOLTIP_BG,
      borderColor: CHART_BORDER,
      borderWidth: 1,
      textStyle: {
        color: 'oklch(0.15 0.005 95)',
        fontSize: 13,
        fontFamily: "'PingFang SC', 'Microsoft YaHei', sans-serif",
      },
    },
    legend: {
      bottom: 0,
      textStyle: {
        color: CHART_TEXT_SECONDARY,
        fontSize: 12,
        fontFamily: "'PingFang SC', 'Microsoft YaHei', sans-serif",
      },
    },
    xAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: {
        color: CHART_TEXT_SECONDARY,
        fontSize: 12,
      },
    },
    yAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: {
        show: true,
        lineStyle: { color: CHART_AXIS, type: 'dashed' as const, width: 1 },
      },
      axisLabel: {
        color: CHART_TEXT_SECONDARY,
        fontSize: 12,
      },
    },
  }
}

/** 饼图专用默认 option 覆盖 */
export function getPieChartOption() {
  return {
    ...getDefaultChartOption(),
    grid: undefined,
    xAxis: undefined,
    yAxis: undefined,
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '50%'],
      label: {
        color: CHART_TEXT_SECONDARY,
        fontSize: 12,
      },
      emphasis: {
        label: { fontSize: 14, fontWeight: 'bold' as const },
      },
    }],
  }
}

/**
 * 对已配置的 ECharts option 合并默认样式。
 * 调用方的 option 优先级更高（Object.assign 后者覆盖前者）。
 */
export function mergeDefaultOption(option: Record<string, unknown>) {
  return Object.assign({}, getDefaultChartOption(), option)
}