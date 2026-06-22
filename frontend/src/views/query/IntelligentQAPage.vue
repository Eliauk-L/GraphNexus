<script setup lang="ts">
import { useQueryStore } from './queryStore'
import ChatInput from './components/ChatInput.vue'
import MarkdownReport from './components/MarkdownReport.vue'
import TokenUsageBar from './components/TokenUsageBar.vue'
import HistoryPanel from './components/HistoryPanel.vue'
import { Loader2 } from '@lucide/vue'

const store = useQueryStore()

function handleSend(question: string) {
  store.sendChat(question)
}
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
    </div>

    <!-- 失败 -->
    <div v-if="store.status === 'failed'" class="error-state supporting">
      {{ store.errorMessage || '请求失败，请重试' }}
    </div>

    <!-- 对话历史 -->
    <div class="qa-history">
      <div v-for="(item, idx) in store.history" :key="idx" class="qa-item">
        <div class="qa-question body-lead">
          <strong>{{ item.question }}</strong>
        </div>
        <MarkdownReport :content="item.answer" :output-format="store.outputFormat" />
        <TokenUsageBar :token-usage="store.tokenUsage" />
      </div>
    </div>

    <!-- 历史记录面板 -->
    <HistoryPanel />
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: var(--spacing-lg);
}

.loading-state {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-2xl) 0;
  justify-content: center;
}

.error-state {
  color: var(--color-error);
  padding: var(--spacing-md) 0;
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