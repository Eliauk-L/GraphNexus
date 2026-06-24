<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, h, computed } from 'vue'
import { NSpace, useMessage, useDialog } from 'naive-ui'
import { useFileStore } from './fileStore'
import FileUpload from './components/FileUpload.vue'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseInput from '@/common/components/BaseInput.vue'
import BaseSelect from '@/common/components/BaseSelect.vue'
import StatusPipeline from './components/StatusPipeline.vue'
import DataTable from '@/common/components/DataTable.vue'
import type { DataTableColumns } from 'naive-ui'
import type { TextbookVO } from '@/api/types'

const store = useFileStore()
const message = useMessage()
const dialog = useDialog()

const page = ref(1)
const pageSize = ref(10)
const searchName = ref('')
const filterFileType = ref<string | null>(null)
const searchSubject = ref<string | null>(null)

const fileTypeOptions = computed(() => {
  const types = [...new Set(store.files.map((f) => f.fileType).filter(Boolean))]
  return [
    { label: '全部类型', value: null },
    ...types.map((t) => ({ label: t, value: t })),
  ]
})

const subjectOptions = computed(() => {
  const subjects = [...new Set(store.files.map((f) => f.subject).filter(Boolean))]
  return [
    { label: '全部学科', value: null },
    ...subjects.map((s) => ({ label: s, value: s })),
  ]
})

const columns: DataTableColumns<TextbookVO> = [
  { title: '文件名', key: 'name', width: 260, ellipsis: { tooltip: true } },
  { title: '类型', key: 'fileType', width: 60 },
  { title: '学科', key: 'subject', width: 80 },
  {
    title: '大小', key: 'fileSize', width: 90,
    render(row) { return formatSize(row.fileSize) },
  },
  {
    title: '状态', key: 'status', width: 120,
    render(row) { return h(StatusPipeline, { status: row.status, failReason: row.failReason }) },
  },
  {
    title: '上传时间', key: 'createTime', width: 160,
    render(row) { return formatTime(row.createTime) },
  },
  {
    title: '操作', key: 'actions', width: 160,
    render(row) {
      return h(NSpace, { size: 'small' }, () => [
        row.status === 'UPLOADED' || row.status === 'FAILED'
          ? h(BaseButton, { size: 'small', onClick: () => handleParse(row.documentId) }, () => '解析')
          : null,
        row.failReason && row.status !== 'FAILED' && row.status !== 'UPLOADED'
          ? h(BaseButton, { size: 'small', onClick: () => handleExtract(row.documentId) }, () => '图谱构建')
          : null,
        h(BaseButton, {
          variant: 'danger' as const, size: 'small',
          onClick: () => handleDelete(row.documentId, row.name),
        }, () => '删除'),
      ])
    },
  },
]

function formatSize(bytes: number): string {
  if (!bytes) return '-'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').substring(0, 19)
}

function load() {
  store.loadFiles(page.value, pageSize.value,
    filterFileType.value || undefined,
    searchName.value || undefined,
    searchSubject.value || undefined,
  )
}

async function handleParse(id: number) {
  try {
    await store.parse(id)
    message.success('已触发解析')
  } catch {
    // handled by store
  }
}

async function handleExtract(id: number) {
  try {
    await store.extract(id)
    message.success('已触发图谱构建')
  } catch {
    // handled by store
  }
}

async function handleDelete(id: number, name: string) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除「${name}」吗？删除后关联的图谱数据将被清除，此操作不可撤销。`,
    positiveText: '确认删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.remove(id)
        message.success(`已删除: ${name}`)
      } catch {
        // handled by store
      }
    },
  })
}

function handleUploadFinish() {
  load()
}

onMounted(() => {
  load()
})

onBeforeUnmount(() => {
  store.stopPolling()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">教材管理</h1>
      <NSpace align="center">
        <FileUpload @uploaded="handleUploadFinish" />
      </NSpace>
    </div>

    <div class="search-bar">
      <BaseSelect
        v-model="filterFileType"
        :options="fileTypeOptions"
        placeholder="文件类型"
        style="width: 120px"
        @update:model-value="load"
      />
      <BaseSelect
        v-model="searchSubject"
        :options="subjectOptions"
        placeholder="学科"
        style="width: 120px"
        @update:model-value="load"
      />
      <BaseInput
        v-model="searchName"
        placeholder="搜索文件名..."
        style="width: 180px"
        @keyup.enter="load"
      />
      <BaseButton @click="load">搜索</BaseButton>
    </div>

    <DataTable
      :columns="columns"
      :data="store.files"
      :loading="store.loading"
      :page="page"
      :page-size="pageSize"
      :total="store.total"
      empty-text="暂无文件，点击上方按钮上传"
      @update:page="(p: number) => { page = p; load(); }"
    />
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

</style>