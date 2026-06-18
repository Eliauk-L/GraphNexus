<script setup lang="ts">
import { ref } from 'vue'
import { NModal, NSpace, useMessage } from 'naive-ui'
import { Upload as UploadIcon } from '@lucide/vue'
import { useFileStore } from '../fileStore'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseInput from '@/common/components/BaseInput.vue'

const emit = defineEmits<{
  uploaded: []
}>()

const store = useFileStore()
const message = useMessage()
const showModal = ref(false)
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

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    selectedFile.value = input.files[0]
  }
}

function handleUpload() {
  showModal.value = true
  selectedFile.value = null
  selectedSubject.value = ''
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

async function confirmUpload() {
  if (!selectedFile.value || !selectedSubject.value.trim()) return
  uploading.value = true
  try {
    await store.upload(selectedFile.value, selectedSubject.value)
    message.success('上传成功')
    showModal.value = false
    selectedFile.value = null
    emit('uploaded')
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

function removeFile() {
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}
</script>

<template>
  <BaseButton @click="handleUpload">
    <UploadIcon :size="16" />
    <span style="margin-left: 4px">上传文件</span>
  </BaseButton>

  <NModal v-model:show="showModal" title="上传文件" style="width: 480px">
    <div class="upload-modal">
      <!-- 学科输入 -->
      <div class="field">
        <label class="label">学科 <span style="color: var(--color-error)">*</span></label>
        <BaseInput
          v-model="selectedSubject"
          placeholder="请输入学科，如 数学"
        />
      </div>

      <!-- 未选择文件 -->
      <div v-if="!selectedFile" class="upload-zone" @click="triggerFileInput">
        <UploadIcon :size="48" color="var(--color-text-tertiary)" />
        <p class="body-lead">点击选择文件</p>
        <p class="supporting" style="color: var(--color-text-tertiary)">
          支持 PDF、TXT 格式，最大 50MB
        </p>
      </div>

      <!-- 已选择文件 -->
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
        accept=".pdf,.txt"
        style="display: none"
        @change="handleFileChange"
      >

      <div class="modal-footer">
        <NSpace justify="end">
          <BaseButton variant="danger" @click="showModal = false">取消</BaseButton>
          <BaseButton :disabled="!selectedFile || !selectedSubject.trim() || uploading" @click="confirmUpload">
            {{ uploading ? '上传中...' : '确认上传' }}
          </BaseButton>
        </NSpace>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
.upload-modal {
  padding: var(--spacing-md);
}

.field {
  margin-bottom: var(--spacing-md);
}

.field .label {
  display: block;
  margin-bottom: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl) var(--spacing-xl);
  border: 2px dashed var(--color-border);
  border-radius: var(--rounded-md);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out);
}

.upload-zone:hover {
  border-color: var(--color-border-focus);
}

.file-selected {
  padding: var(--spacing-md);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
}

.modal-footer {
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border);
}
</style>