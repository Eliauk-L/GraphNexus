<script setup lang="ts">
import { computed } from 'vue'
import { useOpsStore } from '@/stores/opsStore'
import MetricCard from './MetricCard.vue'
import OpsChart from './OpsChart.vue'
import { CHART_COLORS } from '@/common/components/chartTheme'

const store = useOpsStore()

const usage = computed(() => store.summary?.usage)
const loading = computed(() => store.loading)

const pieOption = computed(() => {
  const dist = usage.value?.operationDistribution ?? []
  return {
    tooltip: { trigger: 'item' as const },
    legend: { orient: 'vertical' as const, right: '5%', top: 'center' },
    series: [{
      type: 'pie' as const,
      radius: ['40%', '70%'],
      center: ['40%', '50%'],
      data: dist.map((d, i) => ({
        name: typeLabel(d.type),
        value: d.count,
        itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
      })),
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' as const } },
    }],
  }
})

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    LOGIN: '登录', DOCUMENT_UPLOAD: '文档上传',
    DOCUMENT_PROCESS: '文档处理', QA_ASK: '智能问答',
  }
  return map[type] ?? type
}
</script>

<template>
  <section class="stats-section">
    <h2 class="headline">系统使用量</h2>
    <div class="metric-row">
      <MetricCard label="活跃用户数" :value="usage?.activeUsers ?? 0" :loading="loading" />
      <MetricCard label="登录次数" :value="usage?.loginCount ?? 0" :loading="loading" />
      <MetricCard label="文档操作" :value="(usage?.documentUploadCount ?? 0)" :loading="loading" />
      <MetricCard label="问答次数" :value="usage?.qaAskCount ?? 0" :loading="loading" />
    </div>
    <OpsChart title="操作类型分布" :option="pieOption" :loading="loading"
              :empty="!usage?.operationDistribution?.length" height="280px" />
  </section>
</template>

<style scoped>
.stats-section { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.metric-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--spacing-md); }
</style>