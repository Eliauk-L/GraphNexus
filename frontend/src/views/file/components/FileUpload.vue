<script setup lang="ts">
import { ref } from 'vue'
import { NUpload, NModal, NSpace, useMessage } from 'naive-ui'
import { Upload as UploadIcon } from '@lucide/vue'
import { useFileStore } from '../fileStore'
import BaseButton from '@/common/components/BaseButton.vue'

const props = defineProps<{
  subject: string
}>()

const emit = defineEmits<{
  uploaded: []
}>()

const store = useFileStore()
const message = useMessage()
const showModal = ref(false)
const selectedFile = ref<File | null>(null)
const uploading = ref(false)

function handleFileChange(data: any) {
  selectedFile.value = data.file.file ?? data.file
}

function handleUpload() {
  showModal.value = true
  selectedFile.value = null
}

async function confirmUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    await store.upload(selectedFile.value, props.subject)
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
</script>

<template>
  <BaseButton @click="handleUpload">
    <UploadIcon :size="16" />
    <span style="margin-left: 4px">上传文件</span>
  </BaseButton>

  <NModal v-model:show="showModal" title="上传文件">
    <div style="padding: 16px">
      <NUpload
        :show-file-list="true"
        :max="1"
        accept=".pdf,.csv"
        @change="handleFileChange"
      >
        <div class="upload-zone">
          <UploadIcon :size="48" color="var(--color-text-tertiary)" />
          <p class="body-lead">选择文件或拖拽到此区域</p>
          <p class="supporting" style="color: var(--color-text-tertiary)">
            支持 PDF、CSV 格式，最大 50MB
          </p>
        </div>
      </NUpload>
    </div>
    <template #footer>
      <NSpace justify="end">
        <BaseButton variant="danger" @click="showModal = false">取消</BaseButton>
        <BaseButton :disabled="!selectedFile || uploading" @click="confirmUpload">
          {{ uploading ? '上传中...' : '确认上传' }}
        </BaseButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  border: 2px dashed var(--color-border);
  border-radius: var(--rounded-md);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out);
}
.upload-zone:hover {
  border-color: var(--color-border-focus);
}
</style>