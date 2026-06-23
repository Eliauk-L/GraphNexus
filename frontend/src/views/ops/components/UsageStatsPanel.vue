<script setup lang="ts">
import { computed } from 'vue'
import { useOpsStore } from '@/stores/opsStore'
import MetricCard from './MetricCard.vue'
import OpsChart from './OpsChart.vue'
import { CHART_COLORS } from '@/common/components/chartTheme'
import { NTag } from 'naive-ui'

const store = useOpsStore()
const u = computed(() => store.summary?.usage)
const l = computed(() => store.loading)

const pieOption = computed(() => ({
  tooltip: { trigger: 'item' as const },
  legend: { show: false },
  series: [{
    type: 'pie' as const, radius: ['45%', '75%'], center: ['50%', '50%'],
    data: (u.value?.operationDistribution ?? []).map((d, i) => ({
      name: ({ LOGIN: '登录', DOCUMENT_UPLOAD: '上传', DOCUMENT_PROCESS: '处理', QA_ASK: '问答' } as any)[d.type] ?? d.type,
      value: d.count, itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
    })),
    label: { fontSize: 11 }, emphasis: { label: { fontSize: 13, fontWeight: 'bold' as const } },
  }],
}))
</script>

<template>
  <section class="panel">
    <h2 class="headline">系统使用量 <NTag size="tiny" :bordered="true" style="vertical-align:middle;margin-left:8px">当前</NTag></h2>
    <div class="cards">
      <MetricCard label="活跃用户" :value="u?.activeUsers ?? 0" :loading="l" />
      <MetricCard label="登录次数" :value="u?.loginCount ?? 0" :loading="l" />
      <MetricCard label="文档操作" :value="(u?.documentUploadCount ?? 0) + (u?.documentProcessCount ?? 0)" :loading="l" />
      <MetricCard label="问答次数" :value="u?.qaAskCount ?? 0" :loading="l" />
    </div>
    <OpsChart title="操作类型分布" :option="pieOption" :loading="l" height="220px" />
  </section>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: var(--spacing-md); }
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--spacing-sm); }
</style>