<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useOpsStore } from '@/stores/opsStore'
import { opsApi } from '@/api/ops'
import UsageStatsPanel from './components/UsageStatsPanel.vue'
import DocumentStatsPanel from './components/DocumentStatsPanel.vue'
import GraphStatsPanel from './components/GraphStatsPanel.vue'
import GraphOverviewPanel from './components/GraphOverviewPanel.vue'
import OpsMetricsPanel from './components/OpsMetricsPanel.vue'
import OpsChart from './components/OpsChart.vue'
import { CHART_COLORS } from '@/common/components/chartTheme'
import { NButtonGroup, NButton, NTag, NAlert } from 'naive-ui'

const store = useOpsStore()
const snapshotTriggering = ref(false)

async function triggerSnapshot() {
  snapshotTriggering.value = true
  try {
    await opsApi.triggerSnapshot()
    // 快照完成后局部刷新趋势和摘要
    store.fetchSummary(true)
    store.fetchTrend('active_users', 30)
    store.fetchTrend('document_total', 30)
  } finally { snapshotTriggering.value = false }
}

onMounted(() => {
  store.fetchSummary()
  store.fetchSubjects()
  store.fetchTrend('active_users', 30)
  store.fetchTrend('document_total', 30)
})

function trendOption(metric: string) {
  const data = store.trend?.dataPoints ?? []
  return {
    xAxis: { type: 'category' as const, data: data.map(p => p.date) },
    yAxis: { type: 'value' as const },
    series: [{
      type: 'line' as const,
      data: data.map(p => p.value),
      smooth: true,
      lineStyle: { color: CHART_COLORS[0], width: 2 },
      itemStyle: { color: CHART_COLORS[0] },
      areaStyle: {
        color: {
          type: 'linear' as const, x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'oklch(0.55 0.18 250 / 0.15)' },
            { offset: 1, color: 'oklch(0.55 0.18 250 / 0.02)' },
          ],
        },
      },
    }],
  }
}

const now = new Date()
const timeStr = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
</script>

<template>
  <div class="ops-dashboard">
    <!-- 错误提示 -->
    <NAlert v-if="store.error" type="error" :title="store.error" closable style="margin-bottom: 16px" />

    <!-- 顶部栏：标题 + 粒度切换 -->
    <div class="dashboard-header">
      <h2 class="headline" style="margin: 0">运营管理</h2>
      <div class="header-right">
        <NTag size="tiny" :bordered="true" style="border-color: var(--color-border); color: var(--color-text-tertiary)">
          {{ store.granularity === 'day' ? '天' : store.granularity === 'week' ? '周' : '月' }}粒 · 当前
        </NTag>
        <span class="supporting" style="color: var(--color-text-tertiary); margin-right: 12px">
          最后更新: {{ timeStr }}
        </span>
        <NButtonGroup size="small">
          <NButton
            :type="store.granularity === 'day' ? 'primary' : 'default'"
            @click="store.setGranularity('day')"
          >天</NButton>
          <NButton
            :type="store.granularity === 'week' ? 'primary' : 'default'"
            @click="store.setGranularity('week')"
          >周</NButton>
          <NButton
            :type="store.granularity === 'month' ? 'primary' : 'default'"
            @click="store.setGranularity('month')"
          >月</NButton>
        </NButtonGroup>
      </div>
    </div>

    <!-- 三大区域 -->
    <UsageStatsPanel />
    <DocumentStatsPanel />
    <GraphStatsPanel />

    <!-- 全量图谱可视化 -->
    <GraphOverviewPanel />

    <!-- 图度量指标 -->
    <OpsMetricsPanel />

    <!-- 历史趋势 -->
    <section class="stats-section">
      <div class="section-header">
        <h2 class="headline" style="margin: 0">历史趋势</h2>
        <div style="display:flex;align-items:center;gap:8px">
          <NTag size="tiny" :bordered="true" style="border-color: var(--color-border); color: var(--color-text-tertiary)">
            基于快照数据
          </NTag>
          <NButton size="tiny" :loading="snapshotTriggering" @click="triggerSnapshot">采集快照</NButton>
        </div>
      </div>
      <div class="chart-row">
        <OpsChart title="活跃用户数趋势" :option="trendOption('active_users')" :loading="store.trendLoading" height="300px" />
        <OpsChart title="文档总量趋势" :option="trendOption('document_total')" :loading="store.trendLoading" height="300px" />
      </div>
    </section>
  </div>
</template>

<style scoped>
.ops-dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
  max-width: 1400px;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.stats-section {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.section-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}
</style>