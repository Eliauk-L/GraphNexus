<script setup lang="ts">
import { computed } from 'vue'
import { NTooltip } from 'naive-ui'
import type { FileStatus } from '@/api/types'

const props = defineProps<{
  status: FileStatus
  failReason?: string
}>()

// ── 阶段定义 ──

interface StageState {
  key: string
  label: string
  status: 'completed' | 'active' | 'pending'
}

const stages = computed<StageState[]>(() => {
  const s = props.status
  // 三阶段：解析 / 抽取 / 融合
  const parse: StageState['status'] =
    s === 'PARSING' ? 'active'
    : (s === 'UPLOADED' ? 'pending' : 'completed')

  const extract: StageState['status'] =
    s === 'EXTRACTING' ? 'active'
    : (s === 'UPLOADED' || s === 'PARSING' || s === 'PARSED' ? 'pending' : 'completed')

  const fusion: StageState['status'] =
    s === 'FUSING' ? 'active'
    : (s === 'COMPLETED' ? 'completed' : 'pending')

  return [
    { key: 'parse', label: '解析', status: parse },
    { key: 'extract', label: '抽取', status: extract },
    { key: 'fusion', label: '融合', status: fusion },
  ]
})

// ── 是否为终态失败 ──

const isFailed = computed(() => props.status === 'FAILED')
const isDeleting = computed(() => props.status === 'DELETING')

// ── tooltip 内容 ──

const tooltipText = computed(() => {
  const map: Record<string, string> = {
    UPLOADED: '已上传 — 等待解析',
    PARSING: '解析中 — 正在提取文档文本',
    PARSED: '已解析 — 等待知识抽取',
    EXTRACTING: '抽取中 — LLM 正在抽取知识点和关系边',
    EXTRACTED: '已抽取 — 等待图谱融合',
    FUSING: '融合中 — 正在合并跨文档知识点',
    COMPLETED: '已完成 — 图谱构建全链路完成',
    FAILED: `失败${props.failReason ? '：' + props.failReason : ''}`,
    DELETING: '删除中 — 正在清理关联数据',
  }
  return map[props.status] ?? props.status
})

// ── 截断 failReason ──

const failReasonShort = computed(() => {
  if (!props.failReason) return ''
  return props.failReason.length <= 30 ? props.failReason : props.failReason.substring(0, 30) + '...'
})
</script>

<template>
  <!-- FAILED 回退：红色徽章 -->
  <span v-if="isFailed" class="status-badge status-badge--failed label">
    <span class="status-badge__dot" />
    失败
    <span v-if="failReasonShort" class="status-badge__reason">{{ failReasonShort }}</span>
  </span>

  <!-- DELETING 回退：灰色徽章 -->
  <span v-else-if="isDeleting" class="status-badge status-badge--deleting label">
    删除中
  </span>

  <!-- 管线进度指示器 -->
  <NTooltip v-else placement="top" :delay="300">
    <template #trigger>
      <div class="pipeline">
        <!-- 圆点 + 连接线 -->
        <div class="pipeline__dots">
          <template v-for="(stage, i) in stages" :key="stage.key">
            <span
              class="pipeline__dot"
              :class="`pipeline__dot--${stage.status}`"
              :aria-label="stage.label"
            />
            <span
              v-if="i < stages.length - 1"
              class="pipeline__connector"
              :class="`pipeline__connector--${stage.status === 'completed' && stages[i + 1].status === 'completed' ? 'completed' : 'pending'}`"
            />
          </template>
        </div>
        <!-- 阶段标签 -->
        <div class="pipeline__labels">
          <span
            v-for="stage in stages"
            :key="stage.key"
            class="pipeline__label micro-label"
          >{{ stage.label }}</span>
        </div>
      </div>
    </template>
    <span class="supporting">{{ tooltipText }}</span>
  </NTooltip>
</template>

<style scoped>
/* ── 管线容器 ── */
.pipeline {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
}

/* ── 圆点行 ── */
.pipeline__dots {
  display: inline-flex;
  align-items: center;
  gap: 0;
}

/* ── 圆点 ── */
.pipeline__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--rounded-full);
  transition: background-color var(--duration-base) var(--ease-out),
              border-color var(--duration-base) var(--ease-out);
}

.pipeline__dot--completed {
  background: var(--color-success);
  border: none;
}

.pipeline__dot--active {
  background: var(--color-brand);
  border: none;
  animation: status-pulse var(--duration-slow) var(--ease-out) infinite;
}

.pipeline__dot--pending {
  background: transparent;
  border: 1.5px solid var(--color-text-tertiary);
}

/* ── 连接线 ── */
.pipeline__connector {
  width: 12px;
  height: 1.5px;
  transition: background-color var(--duration-base) var(--ease-out);
}

.pipeline__connector--completed {
  background: var(--color-success);
}

.pipeline__connector--pending {
  background: var(--color-border);
}

/* ── 阶段标签 ── */
.pipeline__labels {
  display: inline-flex;
  justify-content: space-between;
  width: 100%;
}

.pipeline__label {
  color: var(--color-text-tertiary);
}

/* ── 回退徽章（FAILED / DELETING）── */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--rounded-full);
  white-space: nowrap;
  max-width: 120px;
}

.status-badge--failed {
  color: var(--color-error);
  background: oklch(0.50 0.20 25 / 0.12);
}

.status-badge--deleting {
  color: var(--color-text-tertiary);
  background: oklch(0.65 0.005 95 / 0.12);
}

.status-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--rounded-full);
  background: currentColor;
  flex-shrink: 0;
}

.status-badge__reason {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-tertiary);
  text-transform: none;
  letter-spacing: normal;
  max-width: 60px;
}

/* ── 动效 ── */
@keyframes status-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .pipeline__dot--active {
    animation: none;
  }
}
</style>