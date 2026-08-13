<script setup lang="ts">
import VChart from 'vue-echarts'
import { NSkeleton } from 'naive-ui'
import { getDefaultChartOption } from '@/common/components/chartTheme'
import type { EChartsOption } from 'echarts'

const props = withDefaults(defineProps<{
  option: EChartsOption
  title?: string
  loading?: boolean
  height?: string
  empty?: boolean
}>(), {
  title: '',
  loading: false,
  height: '320px',
  empty: false,
})

const chartHeight = props.height

/** 合并默认样式后返回完整 option */
function mergedOption(): EChartsOption {
  const base = getDefaultChartOption()
  return Object.assign({}, base, props.option)
}
</script>

<template>
  <div class="ops-chart">
    <h3 v-if="title" class="title ops-chart__title">{{ title }}</h3>

    <div v-if="loading" class="ops-chart__body" :style="{ height: chartHeight }">
      <NSkeleton height="100%" :sharp="false" />
    </div>

    <div v-else-if="empty" class="ops-chart__body ops-chart__empty" :style="{ height: chartHeight }">
      <span class="supporting" style="color: var(--color-text-tertiary)">暂无数据</span>
    </div>

    <div v-else class="ops-chart__body">
      <VChart
        :option="mergedOption()"
        :style="{ height: chartHeight, width: '100%' }"
        autoresize
      />
    </div>
  </div>
</template>

<style scoped>
.ops-chart {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.ops-chart__title {
  margin: 0;
  font-size: 0.875rem;
}

.ops-chart__title {
  margin: 0;
}

.ops-chart__body {
  flex: 1;
  min-height: 0;
}

.ops-chart__empty {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
