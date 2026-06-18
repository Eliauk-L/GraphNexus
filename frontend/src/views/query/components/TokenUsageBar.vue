<script setup lang="ts">
import type { TokenUsageVO } from '@/api/types'

defineProps<{
  tokenUsage: TokenUsageVO | null
}>()
</script>

<template>
  <div v-if="tokenUsage" class="token-bar supporting">
    <span>📊 {{ tokenUsage.prunedNodes }} 节点</span>
    <span>· {{ tokenUsage.prunedEdges }} 边</span>
    <span>· ≈ {{ tokenUsage.estimatedTokens }} tokens</span>
  </div>
</template>

<style scoped>
.token-bar {
  display: flex;
  gap: var(--spacing-sm);
  color: var(--color-text-tertiary);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border);
  margin-top: var(--spacing-md);
}
</style>