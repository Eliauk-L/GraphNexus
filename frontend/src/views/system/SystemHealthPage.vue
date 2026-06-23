<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { NButton, NCard, NTag, NProgress } from 'naive-ui'
import { RefreshCw } from '@lucide/vue'
import { useSystemStore } from '@/stores/systemStore'

const store = useSystemStore()

onMounted(() => store.startHealthPolling())
onUnmounted(() => store.stopHealthPolling())
</script>

<template>
  <div class="health-page">
    <div class="page-header">
      <div>
        <h1 class="headline">系统健康</h1>
        <p v-if="store.lastRefreshTime" class="supporting" style="color: var(--color-text-tertiary); margin-top: 4px;">
          最后刷新: {{ store.lastRefreshTime }}
        </p>
      </div>
      <NButton secondary size="small" @click="store.fetchHealth()">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </NButton>
    </div>

    <!-- 组件状态卡片行 -->
    <div v-if="store.health?.components" class="component-grid">
      <NCard
        v-for="c in store.health.components"
        :key="c.name"
        size="small"
        :bordered="true"
        class="health-card"
      >
        <div class="card-inner">
          <span class="micro-label" style="color: var(--color-text-secondary);">{{ c.name }}</span>
          <NTag :type="c.status === 'UP' ? 'success' : 'error'" size="small">
            {{ c.status }}
          </NTag>
        </div>
        <div v-if="c.status === 'UP' && c.latency != null" class="mono" style="margin-top: 8px; color: var(--color-text-primary);">
          {{ c.latency }}ms
        </div>
        <div v-else-if="c.status === 'DOWN' && c.error" class="supporting" style="margin-top: 8px; color: var(--color-error);">
          {{ c.error }}
        </div>
      </NCard>
    </div>

    <!-- JVM 指标面板 -->
    <NCard v-if="store.health?.jvm" size="small" :bordered="true" class="jvm-panel">
      <template #header>
        <span class="title">JVM 运行时指标</span>
      </template>
      <div class="jvm-body">
        <div class="jvm-row">
          <span class="micro-label" style="color: var(--color-text-tertiary);">堆内存</span>
          <div style="flex:1; margin: 0 12px;">
            <NProgress
              type="line"
              :percentage="store.health.jvm.heapMax > 0 ? Math.round((store.health.jvm.heapUsed / store.health.jvm.heapMax) * 100) : 0"
              :height="8"
              :border-radius="4"
              :color="'var(--color-brand)'"
              :rail-color="'var(--color-border)'"
            />
          </div>
          <span class="mono">{{ formatBytes(store.health.jvm.heapUsed) }} / {{ formatBytes(store.health.jvm.heapMax) }}</span>
        </div>
        <div class="jvm-grid">
          <div class="metric-item">
            <span class="micro-label" style="color: var(--color-text-tertiary);">CPU</span>
            <span class="mono">{{ (store.health.jvm.cpuUsage * 100).toFixed(1) }}%</span>
          </div>
          <div class="metric-item">
            <span class="micro-label" style="color: var(--color-text-tertiary);">线程活跃</span>
            <span class="mono">{{ store.health.jvm.threadCount }}</span>
          </div>
          <div class="metric-item">
            <span class="micro-label" style="color: var(--color-text-tertiary);">GC 次数</span>
            <span class="mono">{{ store.health.jvm.gcCount }}</span>
          </div>
          <div class="metric-item">
            <span class="micro-label" style="color: var(--color-text-tertiary);">堆最大</span>
            <span class="mono">{{ formatBytes(store.health.jvm.heapMax) }}</span>
          </div>
        </div>
      </div>
    </NCard>
  </div>
</template>

<script lang="ts">
function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const v = bytes / Math.pow(k, i)
  return v.toFixed(i === 0 ? 0 : 1) + ' ' + sizes[i]
}
</script>

<style scoped>
.health-page {
  max-width: 960px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-lg);
}
.component-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}
.health-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
}
.card-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.jvm-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
}
.jvm-body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}
.jvm-row {
  display: flex;
  align-items: center;
}
.jvm-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
}
.metric-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
@media (max-width: 1280px) {
  .component-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .jvm-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>