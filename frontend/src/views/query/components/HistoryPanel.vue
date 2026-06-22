<script setup lang="ts">
import { ref, watch, h, computed } from 'vue'
import {
  NCollapse, NCollapseItem, NInput, NSelect, NDatePicker,
  NButton, NTag, NSpin, NEmpty, NSpace
} from 'naive-ui'
import { Search, FileDown } from '@lucide/vue'
import { useQueryStore } from '../queryStore'
import DataTable from '@/common/components/DataTable.vue'
import MarkdownReport from './MarkdownReport.vue'
import TokenUsageBar from './TokenUsageBar.vue'
import type { HistoryRecordVO, QueryResultResponse } from '@/api/types'
import { getResult } from '@/api/query'
import type { DataTableColumns } from 'naive-ui'

const store = useQueryStore()

// ── 折叠状态 ──
const expandedNames = ref<string[]>([])

// ── 展开详情 ──
const expandedTaskId = ref<string | null>(null)
const expandedAnswer = ref('')
const expandedFormat = ref('markdown')
const expandedLoading = ref(false)

// ── 学科选项 ──
const subjectOptions = computed(() => {
  // 从已有历史记录中提取学科列表（用于筛选下拉）
  const subjects = new Set(store.historyRecords.map(r => r.subject).filter(Boolean))
  return [...subjects].map(s => ({ label: s, value: s }))
})

// ── 状态选项 ──
const statusOptions = [
  { label: '全部', value: '' },
  { label: '完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
]

// ── 筛选表单 ──
const filterForm = ref({
  studentName: '',
  subject: '',
  status: '',
  dateRange: null as [number, number] | null,
})

// ── 同步筛选到 store ──
function applyFilters() {
  store.historyFilters = {
    studentName: filterForm.value.studentName || undefined,
    subject: filterForm.value.subject || undefined,
    status: filterForm.value.status || undefined,
    startDate: filterForm.value.dateRange
      ? new Date(filterForm.value.dateRange[0]).toISOString().slice(0, 10)
      : undefined,
    endDate: filterForm.value.dateRange
      ? new Date(filterForm.value.dateRange[1]).toISOString().slice(0, 10)
      : undefined,
  }
  store.historyPage = 1
  store.loadHistory(1)
}

// ── 表格列定义 ──
const columns: DataTableColumns<HistoryRecordVO> = [
  {
    title: '提问时间', key: 'createTime', width: 150,
    render(row) {
      return row.createTime ? row.createTime.slice(0, 16).replace('T', ' ') : ''
    },
  },
  {
    title: '问题', key: 'question', width: 240, ellipsis: { tooltip: true },
  },
  { title: '学生', key: 'studentName', width: 80 },
  { title: '学科', key: 'subject', width: 70 },
  {
    title: '状态', key: 'status', width: 80,
    render(row) {
      return h(NTag, {
        type: row.status === 'COMPLETED' ? 'success' : 'error',
        size: 'small',
        bordered: false,
      }, { default: () => row.status === 'COMPLETED' ? '完成' : '失败' })
    },
  },
  {
    title: '操作', key: 'actions', width: 80,
    render(row) {
      return h(NButton, {
        size: 'tiny',
        secondary: true,
        onClick: () => store.downloadSingleExport(row.taskId),
      }, { icon: () => h(FileDown, { size: 14 }) })
    },
  },
]

// ── 展开行 ──
async function toggleExpand(taskId: string) {
  if (expandedTaskId.value === taskId) {
    expandedTaskId.value = null
    expandedAnswer.value = ''
    return
  }
  expandedTaskId.value = taskId
  expandedLoading.value = true
  try {
    const result = await getResult(taskId) as QueryResultResponse
    expandedAnswer.value = result.answer || ''
    expandedFormat.value = result.outputFormat || 'markdown'
  } catch {
    expandedAnswer.value = '加载详情失败'
  } finally {
    expandedLoading.value = false
  }
}

// ── 面板展开时加载数据 ──
function handleCollapseChange(names: string[]) {
  expandedNames.value = names
  if (names.includes('history') && store.historyRecords.length === 0 && !store.historyLoading) {
    store.loadHistory(1)
  }
}

// ── 分页变化 ──
function handlePageChange(page: number) {
  store.loadHistory(page)
}
</script>

<template>
  <div class="history-panel">
    <NCollapse
      :expanded-names="expandedNames"
      @update:expanded-names="handleCollapseChange"
    >
      <NCollapseItem name="history">
        <template #header>
          <span class="history-header">历史记录</span>
        </template>

        <!-- 筛选栏 -->
        <NSpace class="filter-bar" align="center" :wrap="true">
          <NInput
            v-model:value="filterForm.studentName"
            placeholder="学生姓名"
            size="small"
            clearable
            style="width: 120px"
          />
          <NSelect
            v-model:value="filterForm.subject"
            :options="subjectOptions"
            placeholder="学科"
            size="small"
            clearable
            style="width: 100px"
          />
          <NSelect
            v-model:value="filterForm.status"
            :options="statusOptions"
            placeholder="状态"
            size="small"
            style="width: 90px"
          />
          <NDatePicker
            v-model:value="filterForm.dateRange"
            type="daterange"
            size="small"
            clearable
            style="width: 220px"
            placeholder="时间范围"
          />
          <NButton size="small" @click="applyFilters">
            <template #icon><Search :size="14" /></template>
            查询
          </NButton>
        </NSpace>

        <!-- 列表 -->
        <DataTable
          :columns="columns"
          :data="store.historyRecords"
          :loading="store.historyLoading"
          :page="store.historyPage"
          :page-size="store.historyPageSize"
          :total="store.historyTotal"
          empty-text="暂无历史诊断记录"
          @update:page="handlePageChange"
        />

        <!-- 展开详情 -->
        <div v-if="expandedTaskId" class="expanded-detail">
          <div class="expanded-divider" />
          <NSpin :show="expandedLoading" size="small">
            <MarkdownReport
              v-if="expandedFormat === 'markdown'"
              :content="expandedAnswer"
              :output-format="expandedFormat"
            />
            <MarkdownReport
              v-else
              :content="expandedAnswer"
              :output-format="expandedFormat"
            />
          </NSpin>
        </div>
      </NCollapseItem>
    </NCollapse>
  </div>
</template>

<style scoped>
.history-panel {
  margin-top: var(--spacing-lg);
}

.history-header {
  font-weight: 600;
  font-size: var(--font-size-md);
}

.filter-bar {
  margin-bottom: var(--spacing-md);
}

.expanded-detail {
  margin-top: var(--spacing-md);
}

.expanded-divider {
  border-top: 1px solid var(--color-border);
  margin-bottom: var(--spacing-md);
}
</style>