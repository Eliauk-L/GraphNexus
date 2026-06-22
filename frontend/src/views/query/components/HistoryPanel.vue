<script setup lang="ts">
import { ref, watch, h, computed } from 'vue'
import {
  NCollapse, NCollapseItem, NInput, NSelect, NDatePicker,
  NButton, NTag, NSpin, NEmpty, NSpace, NPopconfirm, NModal
} from 'naive-ui'
import { Search, FileDown, Trash2, Eye } from '@lucide/vue'
import { useQueryStore } from '../queryStore'
import DataTable from '@/common/components/DataTable.vue'
import MarkdownReport from './MarkdownReport.vue'
import HtmlSvgViewer from '@/common/components/HtmlSvgViewer.vue'
import TokenUsageBar from './TokenUsageBar.vue'
import type { HistoryRecordVO, QueryResultResponse } from '@/api/types'
import { getResult } from '@/api/query'
import type { DataTableColumns } from 'naive-ui'

const store = useQueryStore()

// ── 折叠状态 ──
const expandedNames = ref<string[]>([])

// ── 预览弹窗 ──
const showPreview = ref(false)
const previewTitle = ref('')
const previewAnswer = ref('')
const previewFormat = ref('markdown')
const previewLoading = ref(false)
const previewTokenUsage = ref<any>(null)
const previewError = ref('')

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
      const tag = h(NTag, {
        type: row.status === 'COMPLETED' ? 'success' : 'error',
        size: 'small',
        bordered: false,
      }, { default: () => row.status === 'COMPLETED' ? '完成' : '失败' })

      if (row.status === 'FAILED' && row.errorMessage) {
        return h('span', {
          title: row.errorMessage,
          style: 'cursor: help; border-bottom: 1px dotted var(--color-error);',
        }, [tag])
      }
      return tag
    },
  },
  {
    title: '操作', key: 'actions', width: 150,
    render(row) {
      return h(NSpace, { size: 'small' }, {
        default: () => [
          h(NButton, {
            size: 'tiny',
            secondary: true,
            onClick: () => openPreview(row.taskId, row.question),
          }, { icon: () => h(Eye, { size: 14 }) }),
          h(NButton, {
            size: 'tiny',
            secondary: true,
            onClick: () => store.downloadSingleExport(row.taskId),
          }, { icon: () => h(FileDown, { size: 14 }) }),
          h(NPopconfirm, {
            onPositiveClick: () => store.deleteHistoryRecord(row.taskId),
          }, {
            trigger: () => h(NButton, {
              size: 'tiny',
              tertiary: true,
              type: 'error',
            }, { icon: () => h(Trash2, { size: 14 }) }),
            default: () => '确认删除该条诊断记录？',
          }),
        ],
      })
    },
  },
]

// ── 预览弹窗 ──
async function openPreview(taskId: string, question: string) {
  showPreview.value = true
  previewTitle.value = question
  previewFormat.value = 'markdown'
  previewAnswer.value = ''
  previewTokenUsage.value = null
  previewError.value = ''
  previewLoading.value = true
  try {
    const result = await getResult(taskId) as QueryResultResponse
    if (result.status === 'FAILED') {
      previewError.value = result.errorMessage || '未知错误'
    } else {
      previewAnswer.value = result.answer || ''
      previewFormat.value = result.outputFormat || 'markdown'
      previewTokenUsage.value = result.tokenUsage
    }
  } catch {
    previewError.value = '加载详情失败'
  } finally {
    previewLoading.value = false
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
      </NCollapseItem>
    </NCollapse>

    <!-- 预览弹窗 -->
    <NModal
      v-model:show="showPreview"
      preset="card"
      :title="previewTitle"
      style="max-width: 900px; max-height: 80vh;"
      :segmented="{ content: 'soft', footer: 'soft' }"
      size="huge"
    >
      <NSpin :show="previewLoading" size="medium">
        <!-- 失败记录：显示错误原因 -->
        <div v-if="previewError" class="preview-error">
          <div class="preview-error-label body-lead">诊断失败</div>
          <div class="preview-error-msg supporting">{{ previewError }}</div>
        </div>
        <!-- 成功记录：渲染报告 -->
        <div v-else-if="previewAnswer" class="preview-content">
          <HtmlSvgViewer
            v-if="previewFormat === 'html-svg'"
            :content="previewAnswer"
          />
          <MarkdownReport
            v-else
            :content="previewAnswer"
            :output-format="previewFormat"
          />
          <div v-if="previewTokenUsage" class="preview-token">
            <TokenUsageBar :token-usage="previewTokenUsage" />
          </div>
        </div>
        <NEmpty v-else description="暂无报告内容" />
      </NSpin>
    </NModal>
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

.preview-content {
  min-height: 200px;
}

.preview-error {
  padding: var(--spacing-lg);
}

.preview-error-label {
  color: var(--color-error);
  margin-bottom: var(--spacing-sm);
}

.preview-error-msg {
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-token {
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border);
}
</style>