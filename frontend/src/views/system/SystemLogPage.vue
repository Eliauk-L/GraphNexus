<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton } from 'naive-ui'
import { Download } from '@lucide/vue'
import { useSystemStore } from '@/stores/systemStore'

const store = useSystemStore()
const selectedFile = ref<string | null>(null)

onMounted(() => store.fetchLogFiles())

function selectFile(filename: string) {
  selectedFile.value = filename
  store.fetchLogContent(filename, 1, 200)
}

function prevPage() {
  const c = store.currentLog
  if (c && c.currentPage > 1) {
    store.fetchLogContent(c.fileName, c.currentPage - 1, c.pageSize)
  }
}

function nextPage() {
  const c = store.currentLog
  if (c && c.currentPage < c.totalPages) {
    store.fetchLogContent(c.fileName, c.currentPage + 1, c.pageSize)
  }
}
</script>

<template>
  <div class="log-page">
    <div class="page-header">
      <h1 class="headline">系统日志</h1>
    </div>

    <div class="log-layout">
      <!-- 左栏：文件列表 -->
      <div class="file-list">
        <div
          v-for="f in store.logFiles"
          :key="f.fileName"
          class="file-item"
          :class="{ active: selectedFile === f.fileName, gz: f.fileName.endsWith('.gz') }"
          @click="selectFile(f.fileName)"
        >
          <div class="file-name">{{ f.fileName }}</div>
          <div class="file-meta">{{ f.fileSizeFormatted }} &middot; {{ f.lastModified }}</div>
        </div>
      </div>

      <!-- 右栏：日志内容 -->
      <div class="log-content">
        <template v-if="!store.currentLog">
          <div class="empty-state supporting" style="color: var(--color-text-tertiary);">
            选择一个日志文件查看内容
          </div>
        </template>
        <template v-else>
          <div class="log-toolbar">
            <div class="pagination">
              <NButton secondary size="tiny" :disabled="store.currentLog.currentPage <= 1" @click="prevPage">
                上一页
              </NButton>
              <span class="supporting" style="margin: 0 12px; color: var(--color-text-secondary);">
                第 {{ store.currentLog.currentPage }}/{{ store.currentLog.totalPages }} 页
              </span>
              <NButton
                secondary
                size="tiny"
                :disabled="store.currentLog.currentPage >= store.currentLog.totalPages"
                @click="nextPage"
              >
                下一页
              </NButton>
            </div>
            <NButton secondary size="tiny" @click="store.downloadLog(store.currentLog!.fileName)">
              <template #icon><Download :size="14" /></template>
              下载
            </NButton>
          </div>
          <pre class="log-pre">{{ store.currentLog.lines.join('\n') }}</pre>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.log-page {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}
.page-header {
  margin-bottom: var(--spacing-md);
  flex-shrink: 0;
}
.log-layout {
  display: flex;
  flex: 1;
  min-height: 0;
}
.file-list {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid var(--color-border);
  overflow-y: auto;
  padding-right: var(--spacing-sm);
}
.file-item {
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--rounded-sm);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
}
.file-item:hover {
  background: oklch(0 0 0 / 0.04);
}
.file-item.active {
  background: oklch(0.55 0.18 250 / 0.06);
}
.file-item.gz .file-name {
  color: var(--color-text-tertiary);
}
.file-name {
  font-size: 0.875rem;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-meta {
  font-size: 0.75rem;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.log-content {
  flex: 1;
  padding-left: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.log-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-sm);
  flex-shrink: 0;
}
.pagination {
  display: flex;
  align-items: center;
}
.log-pre {
  flex: 1;
  overflow: auto;
  margin: 0;
  padding: var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--rounded-sm);
  font-family: var(--font-mono);
  font-size: 0.8125rem;
  line-height: 1.5;
  color: var(--color-text-primary);
  white-space: pre;
}
</style>