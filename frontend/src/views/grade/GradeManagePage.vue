<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { NSpace, NModal, useMessage } from 'naive-ui'
import { Search, Trash2, Upload, Settings } from '@lucide/vue'
import { useGradeStore } from './gradeStore'
import type { GradeRecordVO } from '@/api/types'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseInput from '@/common/components/BaseInput.vue'
import DataTable from '@/common/components/DataTable.vue'
import type { DataTableColumns } from 'naive-ui'

const store = useGradeStore()
const message = useMessage()

const page = ref(1)
const pageSize = ref(20)

// ── 上传弹窗 ──
const showUploadModal = ref(false)
const selectedFile = ref<File | null>(null)
const selectedSubject = ref('')
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

// ── 管理考试弹窗 ──
const showManageModal = ref(false)
const manageSearch = ref('')
// 从成绩列表中推导考试元信息
const distinctExams = computed(() => {
  const seen = new Set<string>()
  const list: { examNo: string; examName: string; subject: string; count: number }[] = []
  // 用 store.grades 推导，避免额外 API 调用
  for (const g of store.grades) {
    if (!seen.has(g.examNo)) {
      seen.add(g.examNo)
      list.push({
        examNo: g.examNo,
        examName: g.examName,
        subject: g.subject,
        count: store.grades.filter((r) => r.examNo === g.examNo).length,
      })
    }
  }
  return list
})

const filteredExams = computed(() => {
  if (!manageSearch.value.trim()) return distinctExams.value
  const kw = manageSearch.value.trim().toLowerCase()
  return distinctExams.value.filter(
    (e) => e.examNo.toLowerCase().includes(kw) || e.examName.toLowerCase().includes(kw),
  )
})

const columns: DataTableColumns<GradeRecordVO> = [
  { title: '考试编号', key: 'examNo', width: 130 },
  { title: '考试名称', key: 'examName', width: 160, ellipsis: { tooltip: true } },
  { title: '学号', key: 'studentNo', width: 110 },
  { title: '姓名', key: 'name', width: 80 },
  { title: '班级', key: 'className', width: 110 },
  { title: '学科', key: 'subject', width: 70 },
  { title: '总分', key: 'totalScore', width: 70 },
  { title: '排名', key: 'classRank', width: 60 },
]

async function handleSearch() {
  page.value = 1
  await store.loadGrades(page.value, pageSize.value)
}

// ── 上传 ──
function handleUploadClick() {
  showUploadModal.value = true
  selectedFile.value = null
  selectedSubject.value = ''
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) selectedFile.value = input.files[0]
}

function triggerFileInput() { fileInputRef.value?.click() }
function removeFile() {
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

async function confirmUpload() {
  if (!selectedFile.value || !selectedSubject.value.trim()) return
  uploading.value = true
  try {
    await store.upload(selectedFile.value, selectedSubject.value.trim())
    message.success('成绩上传成功')
    showUploadModal.value = false
    selectedFile.value = null
  } catch { /* handled */ } finally { uploading.value = false }
}

// ── 管理弹窗 ──
function openManageModal() {
  showManageModal.value = true
  manageSearch.value = ''
}

async function confirmDeleteExam(examNo: string) {
  try {
    await store.remove(examNo)
    message.success(`已删除考试 ${examNo}`)
  } catch { /* handled */ }
}

function formatSize(bytes: number): string {
  if (!bytes) return ''
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

onMounted(() => {
  store.loadGrades(page.value, pageSize.value)
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">学生成绩管理</h1>
      <NSpace>
        <BaseButton @click="openManageModal">
          <Settings :size="16" />
          <span style="margin-left: 4px">管理考试</span>
        </BaseButton>
        <BaseButton @click="handleUploadClick">
          <Upload :size="16" />
          <span style="margin-left: 4px">上传成绩</span>
        </BaseButton>
      </NSpace>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <BaseInput v-model="store.filters.examNo" placeholder="考试编号" style="width: 130px" @keyup.enter="handleSearch" />
      <BaseInput v-model="store.filters.examName" placeholder="考试名称" style="width: 130px" @keyup.enter="handleSearch" />
      <BaseInput v-model="store.filters.studentNo" placeholder="学号" style="width: 100px" @keyup.enter="handleSearch" />
      <BaseInput v-model="store.filters.name" placeholder="学生姓名" style="width: 100px" @keyup.enter="handleSearch" />
      <BaseInput v-model="store.filters.className" placeholder="班级" style="width: 100px" @keyup.enter="handleSearch" />
      <BaseInput v-model="store.filters.subject" placeholder="学科" style="width: 80px" @keyup.enter="handleSearch" />
      <BaseButton @click="handleSearch">
        <Search :size="16" style="margin-right: 4px" />查询
      </BaseButton>
    </div>

    <DataTable
      :columns="columns"
      :data="store.grades"
      :loading="store.loading"
      :page="page"
      :page-size="pageSize"
      :total="store.total"
      empty-text="点击查询按钮或上传成绩开始使用"
      @update:page="(p: number) => { page = p; store.loadGrades(p, pageSize); }"
    />

    <!-- 上传弹窗 -->
    <NModal v-model:show="showUploadModal" title="上传成绩" style="width: 480px">
      <div class="upload-modal">
        <div class="field">
          <label class="label">学科 <span style="color: var(--color-error)">*</span></label>
          <BaseInput v-model="selectedSubject" placeholder="请输入学科，如 数学" />
        </div>
        <div v-if="!selectedFile" class="upload-zone" @click="triggerFileInput">
          <Upload :size="48" color="var(--color-text-tertiary)" />
          <p class="body-lead">点击选择成绩文件</p>
          <p class="supporting" style="color: var(--color-text-tertiary)">支持 .csv / .xlsx / .xls 格式</p>
        </div>
        <div v-else class="file-selected">
          <div class="supporting" style="font-weight:500">{{ selectedFile.name }}</div>
          <div class="supporting" style="color:var(--color-text-tertiary);margin-top:4px">{{ formatSize(selectedFile.size) }}</div>
          <BaseButton variant="danger" size="small" style="margin-top:8px" @click="removeFile">移除</BaseButton>
        </div>
        <input ref="fileInputRef" type="file" accept=".csv,.xlsx,.xls" style="display:none" @change="handleFileChange">
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

    <!-- 管理考试弹窗 -->
    <NModal v-model:show="showManageModal" title="管理考试" style="width: 600px">
      <div class="manage-modal">
        <div class="manage-search">
          <BaseInput v-model="manageSearch" placeholder="搜索考试编号或名称..." style="flex:1" @keyup.enter />
        </div>
        <div class="manage-list">
          <div v-if="filteredExams.length === 0" class="supporting" style="color:var(--color-text-tertiary);text-align:center;padding:var(--spacing-2xl)">
            暂无考试记录
          </div>
          <div
            v-for="exam in filteredExams"
            :key="exam.examNo"
            class="manage-row"
          >
            <div class="manage-row__info">
              <span class="supporting" style="font-weight:500">{{ exam.examNo }}</span>
              <span class="supporting" style="color:var(--color-text-secondary);margin-left:var(--spacing-sm)">{{ exam.examName }}</span>
              <span class="supporting" style="color:var(--color-text-tertiary);margin-left:var(--spacing-sm)">{{ exam.subject }} · {{ exam.count }}人</span>
            </div>
            <BaseButton variant="danger" size="small" @click="confirmDeleteExam(exam.examNo)">
              <Trash2 :size="14" style="margin-right:2px" />删除
            </BaseButton>
          </div>
        </div>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.page-header { display:flex; align-items:center; justify-content:space-between; margin-bottom:var(--spacing-lg); }
.search-bar { display:flex; align-items:center; gap:var(--spacing-sm); margin-bottom:var(--spacing-md); flex-wrap:wrap; }
.upload-modal { padding:var(--spacing-md); }
.field { margin-bottom:var(--spacing-md); }
.field .label { display:block; margin-bottom:4px; font-size:0.75rem; font-weight:500; text-transform:uppercase; letter-spacing:0.05em; }
.upload-zone {
  display:flex; flex-direction:column; align-items:center; justify-content:center;
  padding:var(--spacing-2xl) var(--spacing-xl);
  border:2px dashed var(--color-border); border-radius:var(--rounded-md);
  cursor:pointer; transition:border-color var(--duration-fast) var(--ease-out);
}
.upload-zone:hover { border-color:var(--color-border-focus); }
.file-selected { padding:var(--spacing-md); border:1px solid var(--color-border); border-radius:var(--rounded-md); }
.modal-footer { margin-top:var(--spacing-lg); padding-top:var(--spacing-md); border-top:1px solid var(--color-border); }

.manage-modal { padding:var(--spacing-md); }
.manage-search { display:flex; margin-bottom:var(--spacing-md); }
.manage-list { max-height:400px; overflow-y:auto; }
.manage-row {
  display:flex; align-items:center; justify-content:space-between;
  padding:var(--spacing-sm) var(--spacing-md);
  border-bottom:1px solid var(--color-border);
  transition: background var(--duration-fast);
}
.manage-row:hover { background: var(--color-bg); }
</style>