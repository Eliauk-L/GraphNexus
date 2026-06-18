<script setup lang="ts">
import { onMounted, ref, h } from 'vue'
import { NSpace, NModal, useMessage } from 'naive-ui'
import { useGradeStore } from './gradeStore'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseInput from '@/common/components/BaseInput.vue'
import DataTable from '@/common/components/DataTable.vue'
import { Search, Trash2, Upload } from '@lucide/vue'
import type { DataTableColumns } from 'naive-ui'
import type { GradeUploadResultVO } from '@/api/types'

const store = useGradeStore()
const message = useMessage()
const searchExamNo = ref('')
const page = ref(1)
const pageSize = ref(10)

// 上传相关状态
const showUploadModal = ref(false)
const selectedFile = ref<File | null>(null)
const selectedSubject = ref('')
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const subjectOptions = [
  { label: '数学', value: '数学' },
  { label: '语文', value: '语文' },
  { label: '英语', value: '英语' },
  { label: '物理', value: '物理' },
  { label: '化学', value: '化学' },
  { label: '生物', value: '生物' },
  { label: '历史', value: '历史' },
  { label: '地理', value: '地理' },
]

const columns: DataTableColumns<any> = [
  { title: '学号', key: 'studentNo', width: 120 },
  { title: '姓名', key: 'name', width: 100 },
  { title: '班级', key: 'className', width: 120 },
  { title: '总分', key: 'totalScore', width: 80 },
  { title: '排名', key: 'classRank', width: 80 },
  {
    title: '得分明细', key: 'scoreDetails', ellipsis: { tooltip: true },
    render(row: any) { return typeof row.scoreDetails === 'string' ? row.scoreDetails : JSON.stringify(row.scoreDetails) },
  },
]

const examColumns: DataTableColumns<GradeUploadResultVO> = [
  { title: '考试编号', key: 'examNo', width: 140 },
  { title: '考试名称', key: 'examName', width: 200, ellipsis: { tooltip: true } },
  { title: '学科', key: 'subject', width: 80 },
  { title: '考试日期', key: 'examDate', width: 120 },
  { title: '考生数', key: 'studentCount', width: 80 },
  { title: '试题数', key: 'questionCount', width: 80 },
]

async function handleSearch() {
  if (!searchExamNo.value.trim()) return
  await store.searchExamNo(searchExamNo.value.trim())
}

async function handleDelete() {
  try {
    await store.remove(store.examNo)
    message.success('删除成功')
  } catch {
    // handled by store
  }
}

function handleUploadClick() {
  showUploadModal.value = true
  selectedFile.value = null
  selectedSubject.value = ''
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    selectedFile.value = input.files[0]
  }
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function removeFile() {
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

async function confirmUpload() {
  if (!selectedFile.value || !selectedSubject.value.trim()) return
  uploading.value = true
  try {
    await store.upload(selectedFile.value, selectedSubject.value)
    message.success('成绩上传成功')
    showUploadModal.value = false
    selectedFile.value = null
  } catch {
    // handled by store
  } finally {
    uploading.value = false
  }
}

function formatSize(bytes: number): string {
  if (!bytes) return ''
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

onMounted(() => {
  store.loadExams(page.value, pageSize.value)
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">学生成绩管理</h1>
      <BaseButton @click="handleUploadClick">
        <Upload :size="16" />
        <span style="margin-left: 4px">上传成绩</span>
      </BaseButton>
    </div>

    <div class="search-bar">
      <BaseInput
        v-model="searchExamNo"
        placeholder="输入考试编号，如 E20200041"
        style="width: 280px"
        @keyup.enter="handleSearch"
      />
      <BaseButton @click="handleSearch">
        <Search :size="16" style="margin-right: 4px" />
        查询
      </BaseButton>
      <BaseButton
        v-if="store.grades.length > 0"
        variant="danger"
        @click="handleDelete"
        style="margin-left: auto"
      >
        <Trash2 :size="16" style="margin-right: 4px" />
        删除此考试
      </BaseButton>
    </div>

    <div v-if="store.grades.length > 0" class="exam-info supporting" style="color: var(--color-text-secondary)">
      考试 {{ store.examNo }} | {{ store.grades[0].examName }} | 学科: {{ store.grades[0].subject }} | 共 {{ store.grades.length }} 名考生
    </div>

    <DataTable
      :columns="columns"
      :data="store.grades"
      :loading="store.loading"
      empty-text="请先输入考试编号查询"
    />

    <div v-if="store.exams.length > 0" style="margin-top: var(--spacing-2xl)">
      <h2 class="title" style="margin-bottom: var(--spacing-md)">历史考试</h2>
      <DataTable
        :columns="examColumns"
        :data="store.exams"
        empty-text="暂无考试记录"
      />
    </div>

    <!-- 上传成绩弹窗 -->
    <NModal v-model:show="showUploadModal" title="上传成绩" style="width: 480px">
      <div class="upload-modal">
        <div class="field">
          <label class="label">学科 <span style="color: var(--color-error)">*</span></label>
          <BaseInput
            v-model="selectedSubject"
            placeholder="请输入学科，如 数学"
          />
        </div>

        <div v-if="!selectedFile" class="upload-zone" @click="triggerFileInput">
          <Upload :size="48" color="var(--color-text-tertiary)" />
          <p class="body-lead">点击选择 CSV 成绩文件</p>
          <p class="supporting" style="color: var(--color-text-tertiary)">
            支持 .csv 格式，双行表头，最大 50MB
          </p>
        </div>

        <div v-else class="file-selected">
          <div class="supporting" style="font-weight: 500">{{ selectedFile.name }}</div>
          <div class="supporting" style="color: var(--color-text-tertiary); margin-top: 4px">
            {{ formatSize(selectedFile.size) }}
          </div>
          <BaseButton variant="danger" size="small" style="margin-top: 8px" @click="removeFile">
            移除
          </BaseButton>
        </div>

        <input
          ref="fileInputRef"
          type="file"
          accept=".csv"
          style="display: none"
          @change="handleFileChange"
        >

        <div class="modal-footer">
          <NSpace justify="end">
            <BaseButton variant="danger" @click="showUploadModal = false">取消</BaseButton>
            <BaseButton :disabled="!selectedFile || !selectedSubject.trim() || uploading" @click="confirmUpload">
              {{ uploading ? '上传中...' : '确认上传' }}
            </BaseButton>
          </NSpace>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--spacing-lg); }
.search-bar { display: flex; align-items: center; gap: var(--spacing-sm); margin-bottom: var(--spacing-md); }
.exam-info { margin-bottom: var(--spacing-md); }

.upload-modal { padding: var(--spacing-md); }
.field { margin-bottom: var(--spacing-md); }
.field .label {
  display: block; margin-bottom: 4px;
  font-size: 0.75rem; font-weight: 500;
  text-transform: uppercase; letter-spacing: 0.05em;
}
.upload-zone {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: var(--spacing-2xl) var(--spacing-xl);
  border: 2px dashed var(--color-border); border-radius: var(--rounded-md);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out);
}
.upload-zone:hover { border-color: var(--color-border-focus); }
.file-selected {
  padding: var(--spacing-md);
  border: 1px solid var(--color-border); border-radius: var(--rounded-md);
}
.modal-footer {
  margin-top: var(--spacing-lg); padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border);
}
</style>