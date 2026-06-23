<script setup lang="ts">
import { computed } from 'vue'
import { useOpsStore } from '@/stores/opsStore'
import OpsChart from './OpsChart.vue'
import { CHART_COLORS } from '@/common/components/chartTheme'
import { NSelect, NTag } from 'naive-ui'

const store = useOpsStore()
const g = computed(() => store.summary?.graph)
const l = computed(() => store.loading)
const subs = computed(() => [{ label: '全部学科', value: '全部学科' }, ...store.subjects.map(s => ({ label: s, value: s }))])
const nodes = computed(() => g.value?.bySubject?.[store.subject]?.nodes ?? g.value?.nodesByType ?? {})
const edges = computed(() => g.value?.bySubject?.[store.subject]?.edges ?? g.value?.edgesByType ?? {})
const barOption = (data: Record<string, number>) => ({
  tooltip: { trigger: 'axis' as const },
  grid: { top: 8, right: 8, bottom: 32, left: 40 },
  xAxis: { type: 'category' as const, data: Object.keys(data), axisLabel: { rotate: 30, fontSize: 10 } },
  yAxis: { type: 'value' as const },
  series: [{ type: 'bar' as const, data: Object.values(data), itemStyle: { color: CHART_COLORS[0], borderRadius: [4, 4, 0, 0] } }],
})
</script>

<template>
  <section class="panel">
    <div class="head">
      <h2 class="headline" style="margin:0">图谱分布 <NTag size="tiny" :bordered="true" style="vertical-align:middle;margin-left:8px">当前</NTag></h2>
      <NSelect v-model:value="store.subject" :options="subs" size="small" style="width:140px" @update:value="store.setSubject" />
    </div>
    <div class="charts">
      <OpsChart title="节点类型" :option="barOption(nodes)" :loading="l" height="220px" />
      <OpsChart title="边类型" :option="barOption(edges)" :loading="l" height="220px" />
    </div>
  </section>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: var(--spacing-md); }
.head { display: flex; align-items: center; justify-content: space-between; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: var(--spacing-sm); }
</style>