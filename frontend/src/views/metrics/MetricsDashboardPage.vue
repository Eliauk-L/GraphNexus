<script setup lang="ts">
import { onMounted, h } from 'vue'
import { NTabs, NTabPane } from 'naive-ui'
import { useMetricsStore } from './metricsStore'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseCard from '@/common/components/BaseCard.vue'
import DataTable from '@/common/components/DataTable.vue'
import type { DataTableColumns } from 'naive-ui'

const store = useMetricsStore()

const columns: DataTableColumns<any> = [
  { title: '节点 ID', key: 'nodeId', width: 220, ellipsis: { tooltip: true } },
  { title: '节点类型', key: 'nodeType', width: 160 },
  { title: '指标', key: 'metricName', width: 120 },
  {
    title: '数值', key: 'metricValue', width: 140,
    render(row) {
      return h('span', { class: 'mono', style: { color: 'var(--color-text-primary)' } }, row.metricValue?.toFixed(6) ?? '-')
    },
  },
]

function handleQuery() {
  if (store.activeTab === 'pagerank') {
    store.loadPageRank()
  } else {
    store.loadDegree()
  }
}

onMounted(() => {
  store.loadPageRank()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">图指标</h1>
    </div>

    <BaseCard>
      <NTabs
        :value="store.activeTab"
        @update:value="(v: 'pagerank' | 'degree') => store.activeTab = v"
      >
        <NTabPane name="pagerank" tab="PageRank" />
        <NTabPane name="degree" tab="度中心性" />
      </NTabs>

      <div style="margin: var(--spacing-md) 0">
        <BaseButton @click="handleQuery">
          查询{{ store.activeTab === 'pagerank' ? ' PageRank' : ' 度中心性' }}
        </BaseButton>
      </div>

      <DataTable
        :columns="columns"
        :data="store.activeTab === 'pagerank' ? store.pagerankResults : store.degreeResults"
        :loading="store.loading"
        empty-text="点击查询按钮获取指标数据"
        :show-pagination="false"
      />
    </BaseCard>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: var(--spacing-lg);
}
</style>