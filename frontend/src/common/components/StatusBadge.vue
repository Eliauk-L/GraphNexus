<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
}>()

const statusInfo = computed(() => {
  const map: Record<string, { label: string; color: string; bg: string }> = {
    UPLOADED: { label: '已上传', color: 'var(--color-text-secondary)', bg: 'oklch(0.65 0.005 95 / 0.12)' },
    PROCESSING: { label: '处理中', color: 'var(--color-warning)', bg: 'oklch(0.65 0.15 85 / 0.12)' },
    COMPLETED: { label: '已完成', color: 'var(--color-success)', bg: 'oklch(0.55 0.15 145 / 0.12)' },
    FAILED: { label: '失败', color: 'var(--color-error)', bg: 'oklch(0.50 0.20 25 / 0.12)' },
    PENDING: { label: '排队中', color: 'var(--color-text-tertiary)', bg: 'oklch(0.65 0.005 95 / 0.12)' },
  }
  return map[props.status] ?? { label: props.status, color: 'var(--color-text-secondary)', bg: 'oklch(0.65 0.005 95 / 0.08)' }
})
</script>

<template>
  <span
    class="status-badge label"
    :style="{ color: statusInfo.color, backgroundColor: statusInfo.bg }"
  >
    <span
      v-if="props.status === 'PROCESSING'"
      class="status-badge__spinner"
    />
    {{ statusInfo.label }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--rounded-full);
  white-space: nowrap;
}

.status-badge__spinner {
  width: 10px;
  height: 10px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>