<script setup lang="ts">
import { computed } from 'vue'
import { useOpsStore } from '@/stores/opsStore'
import OpsChart from './OpsChart.vue'
import { CHART_COLORS } from '@/common/components/chartTheme'
import { NSelect } from 'naive-ui'

const store = useOpsStore()

const graph = computed(() => store.summary?.graph)
const loading = computed(() => store.loading)
const subjectOptions = computed(() =>
  [{ label: '全部学科', value: '全部学科' },
    ...store.subjects.map(s => ({ label: s, value: s }))]
)

const nodesByType = computed(() => {
  const data = graph.value?.bySubject?.[store.subject]?.nodes ?? graph.value?.nodesByType ?? {}
  return data
})
const edgesByType = computed(() => {
  const data = graph.value?.bySubject?.[store.subject]?.edges ?? graph.value?.edgesByType ?? {}
  return data
})

function barOption(data: Record<string, number>) {
  const keys = Object.keys(data)
  return {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: keys, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' as const },
    series: [{
      type: 'bar' as const,
      data: Object.values(data),
      itemStyle: { color: CHART_COLORS[0], borderRadius: [4, 4, 0, 0] },
    }],
  }
}
</script>

<template>
  <section class="stats-section">
    <div class="section-header">
      <h2 class="headline">图谱分布</h2>
      <NSelect
        v-model:value="store.subject"
        :options="subjectOptions"
        size="small"
        style="width: 160px"
        @update:value="store.setSubject"
      />
    </div>
    <div class="chart-row">
      <OpsChart title="节点类型分布" :option="barOption(nodesByType)" :loading="loading" height="300px" />
      <OpsChart title="边类型分布" :option="barOption(edgesByType)" :loading="loading" height="300px" />
    </div>
  </section>
</template>

<style scoped>
.stats-section { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.section-header { display: flex; align-items: center; justify-content: space-between; }
.section-header .headline { margin: 0; }
.chart-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--spacing-md); }
</style>