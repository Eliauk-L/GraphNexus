<script setup lang="ts">
/**
 * DiagnosisNodeDetail — 诊断子图节点详情滑出面板。
 * 见 UI-DESIGN § 6 DiagnosisNodeDetail 规约。
 */
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { X } from '@lucide/vue'
import ExamTrendChart from './ExamTrendChart.vue'

interface KpNode {
  id: string
  label: string
  weight?: number
  examHistory?: string
}

const props = defineProps<{
  node: KpNode | null
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

// ── 掌握度色阶 ──
function masteryColor(weight: number): string {
  if (weight < 0.4) return 'var(--color-error)'
  if (weight < 0.6) return 'var(--mastery-orange)'
  if (weight < 0.8) return 'var(--mastery-yellow)'
  return 'var(--color-success)'
}

interface TrendDetail {
  examDate: string
  scoreRate: number
}

interface ParsedExamHistory {
  examCount: number
  lastExamDate?: string
  details: TrendDetail[]
}

const parsedHistory = computed<ParsedExamHistory | null>(() => {
  if (!props.node?.examHistory) return null
  try {
    const raw = JSON.parse(props.node.examHistory)
    if (!raw || typeof raw !== 'object') return null
    const details: TrendDetail[] = Array.isArray(raw.details)
      ? raw.details
          .filter((d: any) => d && d.examDate && typeof d.scoreRate === 'number')
          .map((d: any) => ({ examDate: d.examDate, scoreRate: d.scoreRate }))
      : []
    return {
      examCount: typeof raw.examCount === 'number' ? raw.examCount : details.length,
      lastExamDate: typeof raw.lastExamDate === 'string' ? raw.lastExamDate : undefined,
      details,
    }
  } catch {
    return null
  }
})

const parseError = computed(() => {
  if (!props.node?.examHistory) return false
  return parsedHistory.value === null
})

const hasWeight = computed(() => typeof props.node?.weight === 'number')

// ── Escape 关闭 ──
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    emit('close')
  }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Transition name="slide">
    <div v-if="visible && node" class="detail-panel">
      <!-- 标题行 -->
      <div class="detail-header">
        <h3 class="detail-title title">{{ node.label }}</h3>
        <button class="detail-close" @click="emit('close')">
          <X :size="16" />
        </button>
      </div>

      <div class="detail-divider" />

      <!-- Section：当前掌握度 -->
      <div class="detail-section">
        <span class="section-label label">当前掌握度</span>
        <div v-if="hasWeight" class="mastery-row">
          <span
            class="mastery-dot"
            :style="{ background: masteryColor(node.weight!) }"
          />
          <span class="mastery-value mono">{{ node.weight!.toFixed(4) }}</span>
        </div>
        <span v-else class="supporting" style="color: var(--color-text-tertiary)">
          融合数据不可用
        </span>
      </div>

      <!-- Section：考试趋势 -->
      <div class="detail-section">
        <span class="section-label label">考试趋势</span>
        <template v-if="parsedHistory && parsedHistory.details.length >= 2">
          <ExamTrendChart :details="parsedHistory.details" />
        </template>
        <template v-else-if="parseError">
          <span class="supporting" style="color: var(--color-text-tertiary)">
            数据格式异常
          </span>
        </template>
        <template v-else>
          <span class="supporting" style="color: var(--color-text-tertiary)">
            暂无历次考试数据
          </span>
        </template>
      </div>

      <!-- Section：历次考试 -->
      <div v-if="parsedHistory && parsedHistory.details.length > 0" class="detail-section">
        <span class="section-label label">
          历次考试（{{ parsedHistory.examCount }} 次）
        </span>
        <div class="exam-list">
          <div
            v-for="(d, i) in parsedHistory.details"
            :key="i"
            class="exam-row"
          >
            <span class="exam-date supporting">{{ d.examDate }}</span>
            <span class="exam-score mono">{{ (d.scoreRate * 100).toFixed(0) }}%</span>
          </div>
        </div>
      </div>
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

.detail-section {
  margin-bottom: var(--spacing-md);
}

.section-label {
  display: block;
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-xs);
}

.mastery-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.mastery-dot {
  width: 12px;
  height: 12px;
  border-radius: var(--rounded-full);
  flex-shrink: 0;
}

.mastery-value {
  color: var(--color-text-primary);
}

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: var(--spacing-xs);
}

.exam-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.exam-date {
  color: var(--color-text-secondary);
}

.exam-score {
  color: var(--color-text-primary);
}

/* ── slide transition（复用 NodeDetailPanel 动画模式）── */
.slide-enter-active,
.slide-leave-active {
  transition: transform 300ms cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>