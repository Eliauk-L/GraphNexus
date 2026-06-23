<script setup lang="ts">
import { computed } from 'vue'
import { useOpsStore } from '@/stores/opsStore'
import MetricCard from './MetricCard.vue'
import OpsChart from './OpsChart.vue'
import { CHART_COLORS } from '@/common/components/chartTheme'
import { NTag } from 'naive-ui'

const store = useOpsStore()
const d = computed(() => store.summary?.documents)
const l = computed(() => store.loading)

const statusOption = computed(() => ({
  tooltip: { trigger: 'axis' as const },
  grid: { top: 8, right: 8, bottom: 32, left: 40 },
  xAxis: { type: 'category' as const, data: Object.keys(d.value?.byStatus ?? {}), axisLabel: { rotate: 30, fontSize: 10 } },
  yAxis: { type: 'value' as const },
  series: [{ type: 'bar' as const, data: Object.values(d.value?.byStatus ?? {}), itemStyle: { color: CHART_COLORS[0], borderRadius: [4, 4, 0, 0] } }],
}))

const subjectOption = computed(() => ({
  tooltip: { trigger: 'item' as const },
  legend: { show: false },
  series: [{
    type: 'pie' as const, radius: ['40%', '70%'], center: ['50%', '50%'],
    data: Object.entries(d.value?.bySubject ?? {}).map(([k, v], i) => ({ name: k, value: v, itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] } })),
    label: { fontSize: 10 },
  }],
}))
</script>

<template>
  <section class="panel">
    <h2 class="headline">文档处理量 <NTag size="tiny" :bordered="true" style="vertical-align:middle;margin-left:8px">当前</NTag></h2>
    <div class="cards">
      <MetricCard label="文档总数" :value="d?.total ?? 0" :loading="l" />
      <MetricCard label="成绩入库" :value="d?.examTotal ?? 0" :loading="l" />
    </div>
    <div class="charts">
      <OpsChart title="处理状态" :option="statusOption" :loading="l" height="220px" />
      <OpsChart title="学科分布" :option="subjectOption" :loading="l" height="220px" />
    </div>
  </section>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: var(--spacing-md); }
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--spacing-sm); }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: var(--spacing-sm); }
</style>