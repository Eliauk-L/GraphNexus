<script setup lang="ts">
/**
 * MetricsPanel — 考试频次排行面板。
 * 右侧滑出 320px，与 NodeDetailPanel 互斥，slide 动画。
 */
import { computed } from 'vue'
import type { MetricResultVO } from '@/api/types'

const props = defineProps<{
  visible: boolean
  examFrequencyData: MetricResultVO[]
  nodeNames?: Record<string, string>
}>()

const emit = defineEmits<{
  close: []
  'select-node': [nodeId: string]
}>()

interface RankRow {
  nodeId: string
  label: string
  examFrequency: number
}

const rankedRows = computed<RankRow[]>(() => {
  const rows: RankRow[] = []
  for (const d of props.examFrequencyData) {
    rows.push({
      nodeId: d.nodeId,
      label: props.nodeNames?.[d.nodeId] ?? (d.nodeId.length > 8 ? d.nodeId.substring(0, 8) : d.nodeId),
      examFrequency: d.metricValue,
    })
  }
  rows.sort((a, b) => b.examFrequency - a.examFrequency)
  return rows.slice(0, 20)
})

const isEmpty = computed(() => props.examFrequencyData.length === 0)
</script>

<template>
  <Transition name="slide">
    <div v-if="visible" class="metrics-panel">
      <div class="metrics-header">
        <h3 class="metrics-title">考试频次排行</h3>
        <button class="metrics-close" @click="emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <div class="metrics-divider" />

      <div v-if="isEmpty" class="metrics-empty">
        该学科暂无考试记录
      </div>

      <div v-else class="metrics-table-wrap">
        <table class="metrics-table">
          <thead>
            <tr>
              <th class="col-rank">#</th>
              <th class="col-name">知识点</th>
              <th class="col-num">考试频次</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, i) in rankedRows"
              :key="row.nodeId"
              @click="emit('select-node', row.nodeId)"
            >
              <td class="col-rank">{{ i + 1 }}</td>
              <td class="col-name">{{ row.label }}</td>
              <td class="col-num mono">{{ row.examFrequency }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.metrics-panel {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  width: 320px;
  background: var(--color-surface);
  border-left: 1px solid var(--color-border);
  box-shadow: var(--shadow-card-lifted);
  padding: var(--spacing-lg);
  overflow-y: auto;
  z-index: 40;
}

.metrics-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.metrics-title {
  font-size: 1.125rem;
  font-weight: 500;
  color: var(--color-text-primary);
}

.metrics-close {
  background: none;
  border: none;
  color: var(--color-text-tertiary);
  cursor: pointer;
  padding: 4px;
  flex-shrink: 0;
}
.metrics-close:hover {
  color: var(--color-text-primary);
}

.metrics-divider {
  height: 1px;
  background: var(--color-border);
  margin: var(--spacing-md) 0;
}

.metrics-empty {
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  padding: var(--spacing-xl) 0;
}

.metrics-table-wrap {
  overflow-x: auto;
}

.metrics-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.metrics-table thead th {
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
  position: sticky;
  top: 0;
  background: var(--color-surface);
}

.metrics-table tbody td {
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-primary);
}

.metrics-table tbody tr {
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out);
}
.metrics-table tbody tr:hover {
  background: var(--color-brand-veil);
}

.col-rank {
  width: 28px;
  text-align: center;
  color: var(--color-text-tertiary);
}
.col-name {
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160px;
}
.col-num {
  text-align: right;
  font-family: var(--font-mono);
  font-size: 0.8125rem;
}
.mono {
  font-family: var(--font-mono);
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>