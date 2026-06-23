<script setup lang="ts">
import { computed } from 'vue'
import { useOpsStore } from '@/stores/opsStore'
import MetricCard from './MetricCard.vue'
import OpsChart from './OpsChart.vue'
import { CHART_COLORS } from '@/common/components/chartTheme'

const store = useOpsStore()

const docs = computed(() => store.summary?.documents)
const loading = computed(() => store.loading)

const statusBarOption = computed(() => {
  const byStatus = docs.value?.byStatus ?? {}
  return {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: Object.keys(byStatus), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' as const },
    series: [{
      type: 'bar' as const,
      data: Object.values(byStatus),
      itemStyle: { color: CHART_COLORS[0], borderRadius: [4, 4, 0, 0] },
    }],
  }
})

const subjectPieOption = computed(() => {
  const bySubject = docs.value?.bySubject ?? {}
  const entries = Object.entries(bySubject)
  return {
    tooltip: { trigger: 'item' as const },
    legend: { orient: 'vertical' as const, right: '5%', top: 'center' },
    series: [{
      type: 'pie' as const, radius: ['40%', '70%'], center: ['40%', '50%'],
      data: entries.map(([k, v], i) => ({
        name: k, value: v,
        itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
      })),
      label: { show: false },
    }],
  }
})
</script>

<template>
  <section class="stats-section">
    <h2 class="headline">文档处理量</h2>
    <div class="metric-row">
      <MetricCard label="文档总量" :value="docs?.total ?? 0" :loading="loading" />
    </div>
    <div class="chart-row">
      <OpsChart title="按处理状态分布" :option="statusBarOption" :loading="loading" height="280px" />
      <OpsChart title="按学科分布" :option="subjectPieOption" :loading="loading" height="280px" />
    </div>
  </section>
</template>

<style scoped>
.stats-section { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.metric-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--spacing-md); }
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--spacing-md); }
</style>