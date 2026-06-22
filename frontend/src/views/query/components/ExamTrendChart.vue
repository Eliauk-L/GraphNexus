<script setup lang="ts">
/**
 * ExamTrendChart — 微型考试趋势折线图（纯 SVG）。
 * 见 UI-DESIGN § 6 ExamTrendChart 规约 + DESIGN § D5。
 */
import { computed } from 'vue'

interface TrendDetail {
  examDate: string
  scoreRate: number
}

const props = withDefaults(defineProps<{
  details?: TrendDetail[]
}>(), {
  details: () => [],
})

const W = 280
const H = 140
const PAD_LEFT = 36
const PAD_RIGHT = 16
const PAD_TOP = 20
const PAD_BOTTOM = 28

const plotW = W - PAD_LEFT - PAD_RIGHT
const plotH = H - PAD_TOP - PAD_BOTTOM

const shouldRender = computed(() => props.details.length >= 2)

interface Point {
  x: number
  y: number
  dateLabel: string
  scoreLabel: string
}

const points = computed<Point[]>(() => {
  if (props.details.length < 2) return []

  const scores = props.details.map((d) => d.scoreRate)
  const minS = Math.min(...scores)
  const maxS = Math.max(...scores)
  // y 轴范围：min→max 留 10% padding，但不超出 [0, 1]
  const range = maxS - minS
  const yMin = Math.max(0, minS - range * 0.15)
  const yMax = Math.min(1, maxS + range * 0.15)
  const ySpan = yMax - yMin || 1 // 防除零

  return props.details.map((d, i) => {
    const x = PAD_LEFT + (i / Math.max(props.details.length - 1, 1)) * plotW
    const y = PAD_TOP + plotH - ((d.scoreRate - yMin) / ySpan) * plotH
    // 日期缩写：取 "YYYY-MM-DD" → "M月"
    const parts = d.examDate.split('-')
    const month = parts.length >= 2 ? parseInt(parts[1], 10) : 0
    const dateLabel = month ? `${month}月` : d.examDate
    const scoreLabel = d.scoreRate.toFixed(2)
    return { x, y, dateLabel, scoreLabel }
  })
})

const polylinePoints = computed(() =>
  points.value.map((p) => `${p.x},${p.y}`).join(' ')
)
</script>

<template>
  <div v-if="shouldRender" class="trend-chart">
    <svg
      xmlns="http://www.w3.org/2000/svg"
      :viewBox="`0 0 ${W} ${H}`"
      width="280"
      height="140"
    >
      <!-- 基线 y=plotH+PAD_TOP -->
      <line
        :x1="PAD_LEFT"
        :y1="PAD_TOP + plotH"
        :x2="PAD_LEFT + plotW"
        :y2="PAD_TOP + plotH"
        stroke="var(--color-border)"
        stroke-width="1"
      />

      <!-- 折线 -->
      <polyline
        :points="polylinePoints"
        fill="none"
        stroke="var(--color-brand)"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="trend-line"
      />

      <!-- 数据点 + 标注 -->
      <template v-for="(p, i) in points" :key="i">
        <!-- 数据点 -->
        <circle
          :cx="p.x"
          :cy="p.y"
          r="3"
          fill="var(--color-surface)"
          stroke="var(--color-brand)"
          stroke-width="2"
        />
        <!-- 得分率标注（数据点上方） -->
        <text
          :x="p.x"
          :y="p.y - 8"
          text-anchor="middle"
          font-family="var(--font-mono)"
          font-size="9"
          fill="var(--color-text-primary)"
        >{{ p.scoreLabel }}</text>
        <!-- 日期标注（数据点下方） -->
        <text
          :x="p.x"
          :y="PAD_TOP + plotH + 18"
          text-anchor="middle"
          font-family="var(--font-display)"
          font-size="9"
          fill="var(--color-text-tertiary)"
        >{{ p.dateLabel }}</text>
      </template>
    </svg>
  </div>
</template>

<style scoped>
.trend-chart {
  display: flex;
  justify-content: center;
  margin: var(--spacing-sm) 0;
}

/* 入场动画：折线从 0 展开 */
.trend-line {
  stroke-dasharray: 400;
  stroke-dashoffset: 400;
  animation: trend-draw var(--duration-slow) var(--ease-out-quint) forwards;
}

@keyframes trend-draw {
  to {
    stroke-dashoffset: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trend-line {
    animation: none;
    stroke-dasharray: none;
  }
}
</style>