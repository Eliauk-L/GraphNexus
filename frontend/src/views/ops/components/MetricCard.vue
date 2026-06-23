<script setup lang="ts">
import { computed } from 'vue'
import { TrendingUp, TrendingDown, Minus } from '@lucide/vue'
import { NSkeleton } from 'naive-ui'

const props = withDefaults(defineProps<{
  label: string
  value: number | string
  trend?: 'up' | 'down' | 'flat' | null
  trendValue?: string | number
  loading?: boolean
}>(), {
  trend: null,
  trendValue: '',
  loading: false,
})

const trendIcon = computed(() => {
  if (props.trend === 'up') return TrendingUp
  if (props.trend === 'down') return TrendingDown
  if (props.trend === 'flat') return Minus
  return null
})

const trendColor = computed(() => {
  if (props.trend === 'up') return 'var(--color-trend-up)'
  if (props.trend === 'down') return 'var(--color-trend-down)'
  if (props.trend === 'flat') return 'var(--color-trend-flat)'
  return ''
})

const trendText = computed(() => {
  if (props.trend === 'up') return `↑ ${props.trendValue}`
  if (props.trend === 'down') return `↓ ${props.trendValue}`
  if (props.trend === 'flat') return '→ 持平'
  return ''
})
</script>

<template>
  <div class="metric-card">
    <span class="metric-card__label">{{ label }}</span>

    <div v-if="loading" class="metric-card__value">
      <NSkeleton height="36px" width="60%" :sharp="false" />
    </div>
    <span v-else class="metric-card__value display" style="font-size: clamp(1.5rem, 3vw, 2rem)">
      {{ value }}
    </span>

    <div v-if="trend && !loading" class="metric-card__trend">
      <component :is="trendIcon" :size="14" :color="trendColor" />
      <span class="supporting" :style="{ color: trendColor }">{{ trendText }}</span>
    </div>
  </div>
</template>

<style scoped>
.metric-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-card__label {
  font-family: var(--font-display);
  font-size: 0.6875rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-tertiary);
  line-height: 1.2;
}

.metric-card__value {
  color: var(--color-text-primary);
}

.metric-card__trend {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}
</style>