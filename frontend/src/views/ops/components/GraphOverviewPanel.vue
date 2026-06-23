<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { NSpin } from 'naive-ui'
import { Maximize, Minimize } from '@lucide/vue'
import { getFullGraph } from '@/api/graph'
import { toGraphData } from '@/views/graph/graphAdapter'
import type { GraphSubgraphVO } from '@/api/types'
import { Graph } from '@antv/g6'
import {
  DEFAULT_NODE_SIZE,
  DEFAULT_NODE_COLOR,
  DEFAULT_EDGE_COLOR,
  DEFAULT_EDGE_WIDTH,
  DEFAULT_EDGE_OPACITY,
} from '@/views/graph/constants'

const loading = ref(false)
const error = ref<string | null>(null)
const containerRef = ref<HTMLDivElement>()
const graphContainerRef = ref<HTMLDivElement>()
const isFullscreen = ref(false)
let graph: Graph | null = null
let resizeObserver: ResizeObserver | null = null

// ── 全屏切换 ──

async function toggleFullscreen() {
  if (!graphContainerRef.value) return
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await graphContainerRef.value.requestFullscreen()
    }
  } catch (e) {
    console.warn('[OpsGraph] fullscreen error:', e)
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
  setTimeout(() => {
    if (graph) {
      try { graph.setSize(containerRef.value!.clientWidth, containerRef.value!.clientHeight) } catch { /* ignore */ }
    }
  }, 200)
}

// ── 加载全量图谱 ──

async function loadGraph() {
  loading.value = true
  error.value = null
  try {
    const data: GraphSubgraphVO = await getFullGraph()
    const g6Data = toGraphData(data)
    if (!g6Data || !g6Data.nodes.length) {
      error.value = '暂无图谱数据'
      loading.value = false
      return
    }

    if (graph) { graph.destroy(); graph = null }
    loading.value = false
    await nextTick()

    if (!containerRef.value) return
    const w = containerRef.value.clientWidth
    const h = containerRef.value.clientHeight
    if (!w || !h) return

    graph = new Graph({
      container: containerRef.value,
      width: w,
      height: h,
      data: {
        nodes: g6Data.nodes.map(n => ({ id: n.id, data: { ...n.data } })),
        edges: g6Data.edges.map(e => ({ id: e.id, source: e.source, target: e.target, data: { ...e.data } })),
      },
      autoFit: 'view',
      node: {
        type: 'circle',
        style: {
          size: (d: any) => d.data?.size ?? DEFAULT_NODE_SIZE,
          fill: (d: any) => d.data?.color ?? DEFAULT_NODE_COLOR,
          stroke: '#fff',
          lineWidth: 2,
          labelText: (d: any) => d.data?.label ?? '',
          labelFontSize: 11,
          labelFill: '#333',
          labelPlacement: 'bottom',
          labelOffsetY: 6,
        },
      },
      edge: {
        type: 'line',
        style: {
          stroke: (d: any) => d.data?.color ?? DEFAULT_EDGE_COLOR,
          lineWidth: (d: any) => d.data?.width ?? DEFAULT_EDGE_WIDTH,
          opacity: DEFAULT_EDGE_OPACITY,
        },
      },
      layout: {
        type: 'force',
        preventOverlap: true,
        nodeStrength: -300,
        edgeStrength: 0.05,
        linkDistance: 150,
      },
      behaviors: ['zoom-canvas', 'drag-canvas', 'drag-element'],
      animation: true,
    })

    await graph.render()
  } catch (e: any) {
    error.value = e?.message ?? '图谱加载失败'
    loading.value = false
  }
}

// ── resize ──

function setupResizeObserver() {
  if (!containerRef.value) return
  resizeObserver = new ResizeObserver(() => {
    if (!graph || !containerRef.value) return
    const w = containerRef.value.clientWidth
    const h = containerRef.value.clientHeight
    if (w > 0 && h > 0) graph.setSize(w, h)
  })
  resizeObserver.observe(containerRef.value)
}

// ── lifecycle ──

onMounted(() => {
  loadGraph()
  setupResizeObserver()
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
  if (graph) { graph.destroy(); graph = null }
})
</script>

<template>
  <div ref="graphContainerRef" class="graph-overview" :class="{ fullscreen: isFullscreen }">
    <div class="graph-overview__header">
      <h3 class="title" style="margin:0">全量图谱可视化</h3>
      <div class="graph-overview__actions">
        <span class="supporting" style="color:var(--color-text-tertiary)">Neo4j 全部节点和边</span>
        <button class="btn-fullscreen" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏'">
          <Minimize v-if="isFullscreen" :size="16" />
          <Maximize v-else :size="16" />
        </button>
      </div>
    </div>

    <div class="graph-overview__body">
      <div v-show="loading" class="graph-overview__overlay">
        <NSpin />
      </div>
      <div v-show="error && !loading" class="graph-overview__overlay" style="pointer-events:none">
        <span class="supporting" style="color:var(--color-text-tertiary)">{{ error }}</span>
      </div>
      <div ref="containerRef" class="graph-overview__canvas" :style="{ height: isFullscreen ? '100vh' : '560px' }" />
    </div>

    <!-- 图例 -->
    <div class="graph-overview__legend">
      <span class="legend-item"><i style="background:#3B82F6"></i>知识点</span>
      <span class="legend-item"><i style="background:#F97316"></i>实体</span>
      <span class="legend-item"><i style="background:#EAB308"></i>分类</span>
      <span class="legend-item"><i style="background:#EC4899"></i>学生</span>
      <span class="legend-item"><i style="background:#8B5CF6"></i>考试</span>
      <span class="legend-item"><i style="background:#10B981"></i>文档</span>
      <span class="legend-item"><i style="background:#06B6D4"></i>学科</span>
    </div>
  </div>
</template>

<style scoped>
.graph-overview {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-lg);
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.graph-overview.fullscreen {
  position: fixed;
  inset: 0;
  z-index: 9999;
  border-radius: 0;
  padding: var(--spacing-md);
  background: var(--color-bg);
}

.graph-overview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.graph-overview__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.btn-fullscreen {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-sm);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
              border-color var(--duration-fast) var(--ease-out);
}

.btn-fullscreen:hover {
  color: var(--color-brand);
  border-color: var(--color-border-focus);
}

.graph-overview__body {
  position: relative;
}

.graph-overview__canvas {
  width: 100%;
}

.graph-overview__overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface);
}

.graph-overview__legend {
  display: flex;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--color-border);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
  color: var(--color-text-secondary);
}

.legend-item i {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>