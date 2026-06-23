<script setup lang="ts">
/**
 * NodeDetailPanel — 节点详情侧边滑出面板。
 * 见 UI-DESIGN §4.3。
 */

defineProps<{
  node: { id: string; data: Record<string, unknown> } | null
  visible: boolean
  metrics?: { examFrequency?: number } | null
}>()

defineEmits<{
  close: []
}>()

/** 不在详情面板展示的字段 */
const HIDDEN_KEYS = new Set([
  // G6 internal
  'label', 'color', 'size',
  'x', 'y', 'z',
  'states', 'style',
  // 已单独展示
  'nodeType', 'name',
  // 内部标识/时间戳，无需展示
  'id', 'documentId', 'createdAt', 'updatedAt',
  // 代码/配置类
  'nodeType',
  // 文档页码（无业务意义）
  'pageNumber',
])

/** 仅展示有业务含义的属性 */
const SHOW_KEYS = new Set([
  'description',
  'originalText',
  'entityType',
  'subject',
  'gradeLevel',
  'weight',
  'className',
  'studentNo',
  'examNo',
  'examName',
  'totalScore',
  'classRank',
])

function shouldShowProperty(key: string, value: unknown): boolean {
  if (HIDDEN_KEYS.has(key)) return false
  // 如果在白名单中，直接展示
  if (SHOW_KEYS.has(key)) return true
  if (value === undefined || value === null) return false
  if (value === '') return false
  if (typeof value === 'object') return false
  // 非白名单字段默认隐藏（防止未预期的内部字段泄露）
  return false
}

function formatValue(value: unknown): string {
  if (typeof value === 'number') {
    // 截断浮点数
    return Number.isInteger(value) ? String(value) : (value as number).toFixed(4)
  }
  return String(value)
}
</script>

<template>
  <Transition name="slide">
    <div v-if="visible && node" class="detail-panel">
      <div class="detail-header">
        <h3 class="detail-title title">{{ (node.data.name as string) ?? (node.data.label as string) ?? node.id }}</h3>
        <button class="detail-close" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <div class="detail-divider" />

      <div class="detail-fields">
        <div class="detail-field">
          <span class="detail-field-label supporting">ID</span>
          <span class="detail-field-value body mono">{{ node.id }}</span>
        </div>
        <div class="detail-field">
          <span class="detail-field-label supporting">类型</span>
          <span class="detail-field-value body">{{ node.data.nodeType }}</span>
        </div>
        <template v-for="(value, key) in node.data" :key="key">
          <div
            v-if="shouldShowProperty(key, value)"
            class="detail-field"
          >
            <span class="detail-field-label supporting">{{ key }}</span>
            <span class="detail-field-value body">{{ formatValue(value) }}</span>
          </div>
        </template>
      </div>

      <!-- 考试频次 -->
      <template v-if="metrics?.examFrequency != null">
        <div class="detail-divider" />
        <div class="detail-fields">
          <span class="detail-field-label supporting">度量指标</span>
          <div class="detail-field">
            <span class="detail-field-label supporting">考试频次</span>
            <span class="detail-field-value body mono">{{ metrics.examFrequency }}</span>
          </div>
        </div>
      </template>

    </div>
  </Transition>
</template>

<style scoped>
.detail-panel {
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

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.detail-title {
  font-size: 1.125rem;
  font-weight: 500;
  color: var(--color-text-primary);
  word-break: break-all;
}

.detail-close {
  background: none;
  border: none;
  color: var(--color-text-tertiary);
  cursor: pointer;
  padding: 4px;
  flex-shrink: 0;
}
.detail-close:hover {
  color: var(--color-text-primary);
}

.detail-divider {
  height: 1px;
  background: var(--color-border);
  margin: var(--spacing-md) 0;
}

.detail-fields {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-field-label {
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.detail-field-value {
  color: var(--color-text-primary);
  font-size: 1rem;
  word-break: break-all;
}

.mono {
  font-family: var(--font-mono);
}

/* slide transition */
.slide-enter-active,
.slide-leave-active {
  transition: transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>