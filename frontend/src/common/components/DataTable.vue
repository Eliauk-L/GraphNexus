<script setup lang="ts" generic="T extends Record<string, unknown>">
import { NDataTable, NPagination, NEmpty, NSpin } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { Archive } from '@lucide/vue'
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  columns: DataTableColumns<T>
  data: T[]
  loading?: boolean
  emptyText?: string
  page?: number
  pageSize?: number
  total?: number
  showPagination?: boolean
}>(), {
  loading: false,
  emptyText: '暂无数据',
  page: 1,
  pageSize: 10,
  total: 0,
  showPagination: true,
})

const emit = defineEmits<{
  'update:page': [page: number]
  'update:pageSize': [pageSize: number]
}>()

const paginationProps = computed(() => ({
  page: props.page,
  pageSize: props.pageSize,
  itemCount: props.total,
  simple: false,
  showSizePicker: false,
}))
</script>

<template>
  <NSpin :show="loading">
    <NDataTable
      :columns="columns"
      :data="data"
      :loading="loading"
      :bordered="false"
      :single-line="false"
      size="small"
    >
      <template #empty>
        <NEmpty :description="emptyText">
          <template #icon>
            <Archive :size="48" color="var(--color-text-tertiary)" />
          </template>
        </NEmpty>
      </template>
    </NDataTable>
    <div v-if="showPagination && total > pageSize" class="data-table__pagination">
      <NPagination
        :page="page"
        :page-size="pageSize"
        :item-count="total"
        @update:page="emit('update:page', $event)"
      />
    </div>
  </NSpin>
</template>

<style scoped>
.data-table__pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-md);
}
</style>