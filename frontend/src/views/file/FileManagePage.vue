<script setup lang="ts">
import { onMounted, ref, h } from 'vue'
import { NSpace, NModal, useMessage } from 'naive-ui'
import { Upload, Trash2, Play } from '@lucide/vue'
import { useFileStore } from './fileStore'
import type { GradeUploadResultVO } from '@/api/types'
import FileUpload from './components/FileUpload.vue'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseSelect from '@/common/components/BaseSelect.vue'
import StatusBadge from '@/common/components/StatusBadge.vue'
import DataTable from '@/common/components/DataTable.vue'
import type { DataTableColumns } from 'naive-ui'

const store = useFileStore()
const message = useMessage()

const page = ref(1)
const pageSize = ref(10)
const selectedSubject = ref('数学')

const columns: DataTableColumns<any> = [
  { title: '文件名', key: 'name', width: 280, ellipsis: { tooltip: true } },
  { title: '学科', key: 'subject', width: 80 },
  {
    title: '大小', key: 'fileSize', width: 90,
    render(row) { return formatSize(row.fileSize) },
  },
  {
    title: '状态', key: 'status', width: 100,
    render(row) { return h(StatusBadge, { status: row.status }) },
  },
  {
    title: '上传时间', key: 'createTime', width: 160,
    render(row) { return formatTime(row.createTime) },
  },
  {
    title: '操作', key: 'actions', width: 140,
    render(row) {
      return h(NSpace, { size: 'small' }, () => [
        row.status === 'UPLOADED'
          ? h(BaseButton, { size: 'small', onClick: () => handleProcess(row.documentId) }, () => '解析')
          : null,
        h(BaseButton, {
          variant: 'danger', size: 'small',
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

async function handleProcess(id: number) {
  try {
    await store.process(id)
    message.success('解析完成')
  } catch {
    // error already handled by store
  }
}

async function handleDelete(id: number, name: string) {
  try {
    await store.remove(id)
    message.success(`已删除: ${name}`)
  } catch {
    // error already handled by store
  }
}

async function handleUploadFinish() {
  await store.loadFiles(page.value, pageSize.value)
}

onMounted(() => {
  store.loadFiles(page.value, pageSize.value)
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">文件管理</h1>
      <NSpace>
        <FileUpload :subject="selectedSubject" @uploaded="handleUploadFinish" />
      </NSpace>
    </div>

    <DataTable
      :columns="columns"
      :data="store.files"
      :loading="store.loading"
      :page="page"
      :page-size="pageSize"
      :total="store.total"
      empty-text="暂无文件，点击上方按钮上传"
      @update:page="(p) => { page = p; store.loadFiles(p, pageSize); }"
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
</style>