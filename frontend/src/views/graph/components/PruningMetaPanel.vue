<script setup lang="ts">
/**
 * PruningMetaPanel — 剪枝子图元信息条。
 * 仅在剪枝子图视图且 meta 非空时显示。
 * 见 UI-DESIGN §4.4。
 */
import type { PruningMetaVO } from '@/api/types'

defineProps<{
  meta: PruningMetaVO | null
}>()
</script>

<template>
  <div v-if="meta" class="pruning-meta">
    <div class="meta-item">
      <span class="meta-label">策略</span>
      <span class="meta-value">{{ meta.strategy }}</span>
    </div>
    <div class="meta-item">
      <span class="meta-label">阈值</span>
      <span class="meta-value">{{ meta.weakThreshold }}</span>
    </div>
    <div class="meta-item">
      <span class="meta-label">跳数</span>
      <span class="meta-value">{{ meta.maxHops }}</span>
    </div>
    <div class="meta-item">
      <span class="meta-label">节点</span>
      <span class="meta-value">{{ meta.totalNodes }}</span>
    </div>
    <div class="meta-item">
      <span class="meta-label">边</span>
      <span class="meta-value">{{ meta.totalEdges }}</span>
    </div>
    <div class="meta-item" v-if="meta.truncated">
      <span class="meta-label" style="color: var(--color-warning)">截断</span>
      <span class="meta-value" style="color: var(--color-warning)">是</span>
    </div>
    <div v-if="meta.truncated && meta.truncatedNodeNames?.length" class="meta-truncated-list">
      <span class="meta-label">截断节点：</span>
      <span class="meta-value">{{ meta.truncatedNodeNames.join(', ') }}</span>
    </div>
  </div>
</template>

<style scoped>
.pruning-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-brand-veil);
  border-radius: var(--rounded-md);
  margin-bottom: var(--spacing-md);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.meta-value {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.meta-truncated-list {
  width: 100%;
  font-size: 0.75rem;
  color: var(--color-text-tertiary);
  padding-top: 4px;
  border-top: 1px solid var(--color-border);
}
</style>