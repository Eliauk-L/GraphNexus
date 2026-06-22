<script setup lang="ts">
import { ref, watch } from 'vue'
import { useQueryStore } from './queryStore'
import ChatInput from './components/ChatInput.vue'
import MarkdownReport from './components/MarkdownReport.vue'
import TokenUsageBar from './components/TokenUsageBar.vue'
import HistoryPanel from './components/HistoryPanel.vue'
import DiagnosisSubgraph from './components/DiagnosisSubgraph.vue'
import DiagnosisNodeDetail from './components/DiagnosisNodeDetail.vue'
import { Loader2, AlertCircle, X } from '@lucide/vue'

const store = useQueryStore()

function handleSend(question: string) {
  store.sendChat(question)
}

const showErrorDetail = ref(false)

// 诊断完成后自动加载子图
watch(() => store.status, (newStatus) => {
  if (newStatus === 'completed' && store.taskId) {
    store.loadSubgraph(store.taskId)
  }
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">学情诊断</h1>
    </div>

    <ChatInput
      :disabled="store.status === 'processing' || store.status === 'pending'"
      @send="handleSend"
    />

    <!-- 处理中 -->
    <div v-if="store.status === 'processing' || store.status === 'pending'" class="loading-state">
      <Loader2 :size="24" class="spin" color="var(--color-brand)" />
      <span class="body">分析中，请稍候...</span>
      <p class="loading-hint supporting">正在同步分析，请勿离开或刷新页面</p>
    </div>

    <!-- 失败 -->
    <div
      v-if="store.status === 'failed'"
      class="error-state"
      @mouseenter="showErrorDetail = true"
      @mouseleave="showErrorDetail = false"
    >
      <div class="error-summary">
        <AlertCircle :size="16" color="var(--color-error)" />
        <span class="supporting">{{ store.errorMessage || '请求失败，请重试' }}</span>
      </div>
      <div v-if="showErrorDetail" class="error-detail supporting">
        <p>请检查输入是否正确，或稍后重试。如问题持续存在，请联系管理员并提供上方问题描述。</p>
        <button class="error-dismiss micro-label" @click="store.clearError()">
          <X :size="12" /> 关闭
        </button>
      </div>
    </div>

    <!-- 对话历史 -->
    <div class="qa-history">
      <div v-for="(item, idx) in store.history" :key="idx" class="qa-item">
        <div class="qa-question body-lead">
          <strong>{{ item.question }}</strong>
        </div>
        <MarkdownReport :content="item.answer" :output-format="store.outputFormat" />
        <TokenUsageBar :token-usage="store.tokenUsage" />
        <!-- 子图可视化：仅在最后一条诊断完成时展示 -->
        <DiagnosisSubgraph
          v-if="idx === store.history.length - 1 && store.status === 'completed' && store.taskId"
          :task-id="store.taskId"
          @node-click="(node) => store.selectedKpNode = node"
        />
      </div>
    </div>

    <!-- 历史记录面板 -->
    <HistoryPanel />

    <!-- 子图节点详情面板 -->
    <DiagnosisNodeDetail
      :node="store.selectedKpNode"
      :visible="store.selectedKpNode !== null"
      @close="store.selectedKpNode = null"
    />
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: var(--spacing-lg);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-2xl) 0;
  justify-content: center;
}

.loading-hint {
  color: var(--color-text-tertiary);
  margin-top: var(--spacing-xs);
}

.error-state {
  padding: var(--spacing-md);
  margin: var(--spacing-md) 0;
  background: oklch(0.50 0.20 25 / 0.06);
  border: 1px solid oklch(0.50 0.20 25 / 0.15);
  border-radius: var(--rounded-md);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
}

.error-state:hover {
  background: oklch(0.50 0.20 25 / 0.10);
}

.error-summary {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  color: var(--color-error);
}

.error-detail {
  margin-top: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  border-top: 1px solid oklch(0.50 0.20 25 / 0.12);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.error-dismiss {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: var(--spacing-sm);
  padding: 2px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
}

.error-dismiss:hover {
  color: var(--color-text-primary);
  border-color: var(--color-text-tertiary);
}

.qa-history {
  margin-top: var(--spacing-xl);
}

.qa-item {
  margin-bottom: var(--spacing-2xl);
  padding-bottom: var(--spacing-xl);
  border-bottom: 1px solid var(--color-border);
}

.qa-question {
  padding: var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--rounded-md);
  margin-bottom: var(--spacing-md);
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>