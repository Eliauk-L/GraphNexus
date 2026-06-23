<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { NSpin } from 'naive-ui'
import { Maximize, Minimize, X } from '@lucide/vue'
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
  NODE_COLORS,
  EDGE_COLORS,
} from '@/views/graph/constants'

const loading = ref(false)
const error = ref<string | null>(null)
const containerRef = ref<HTMLDivElement>()
const graphContainerRef = ref<HTMLDivElement>()
const isFullscreen = ref(false)
let graph: Graph | null = null
let resizeObserver: ResizeObserver | null = null

// ── 详情面板 ──

type DetailType = 'node' | 'edge'
interface DetailInfo {
  type: DetailType
  id: string
  label: string
  nodeType?: string
  edgeType?: string
  color: string
  properties: [string, string][]
}

const detail = ref<DetailInfo | null>(null)

const NODE_TYPE_LABELS: Record<string, string> = {
  KnowledgePoint: '知识点', Entity: '实体', KnowledgeCategory: '分类',
  Student: '学生', Exam: '考试', Document: '文档', Subject: '学科',
}
const EDGE_TYPE_LABELS: Record<string, string> = {
  ALIGNED_TO: '实体对齐', PREREQUISITE_OF: '前置依赖',
  BELONGS_TO: '类别归属', BELONGS_TO_SUBJECT: '学科归属',
  CHILD_OF: '层级关系', MASTERS: '掌握度', TESTED: '考试考查',
  REFERENCES: '引用', EXTRACTS: '文档抽取', DERIVES: '推导', CONTAINS: '包含',
}
const HIDDEN_KEYS = new Set(['label', 'color', 'size', 'nodeType', 'edgeType', 'id',
  'documentId', 'createdAt', 'updatedAt', 'pageNumber', 'x', 'y', 'z', 'states', 'style',
  'sourceNodeId', 'targetNodeId'])

function closeDetail() { detail.value = null }

function formatVal(v: unknown): string {
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : v.toFixed(4)
  if (v === null || v === undefined) return ''
  return String(v)
}

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

    // ── 点击事件 ──
    graph.on('node:click', (evt: any) => {
      const id = evt.target?.id
      const nodeData = g6Data.nodes.find(n => n.id === id)
      if (!nodeData) return
      const data = nodeData.data as Record<string, unknown>
      const props: [string, string][] = Object.entries(data)
        .filter(([k, v]) => !HIDDEN_KEYS.has(k) && v !== null && v !== undefined && v !== '' && typeof v !== 'object')
        .map(([k, v]) => [k, formatVal(v)])
      detail.value = {
        type: 'node',
        id,
        label: (data.label ?? data.name ?? id) as string,
        nodeType: data.nodeType as string,
        color: (NODE_COLORS[data.nodeType as string] ?? DEFAULT_NODE_COLOR) as string,
        properties: props,
      }
    })

    graph.on('edge:click', (evt: any) => {
      const id = evt.target?.id
      const edgeData = g6Data.edges.find(e => e.id === id)
      if (!edgeData) return
      const data = edgeData.data as Record<string, unknown>
      const edgeType = data.edgeType as string
      detail.value = {
        type: 'edge',
        id,
        label: `${edgeType ?? '边'}  ${edgeData.source} → ${edgeData.target}`,
        edgeType,
        color: (EDGE_COLORS[edgeType] ?? DEFAULT_EDGE_COLOR) as string,
        properties: [],
      }
    })

    graph.on('canvas:click', () => { closeDetail() })

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

      <!-- 详情面板 -->
      <Transition name="slide-up">
        <div v-if="detail" class="detail-card">
          <div class="detail-card__header">
            <div class="detail-card__title-row">
              <span class="detail-card__dot" :style="{ background: detail.color }"></span>
              <strong class="supporting">{{ detail.label }}</strong>
            </div>
            <button class="detail-card__close" @click="closeDetail"><X :size="14" /></button>
          </div>
          <div class="detail-card__meta">
            <span v-if="detail.type === 'node' && detail.nodeType" class="detail-tag">
              {{ NODE_TYPE_LABELS[detail.nodeType] ?? detail.nodeType }}
            </span>
            <span v-if="detail.type === 'edge' && detail.edgeType" class="detail-tag detail-tag--edge">
              {{ detail.edgeType }} {{ EDGE_TYPE_LABELS[detail.edgeType] ? '· ' + EDGE_TYPE_LABELS[detail.edgeType] : '' }}
            </span>
          </div>
          <table v-if="detail.properties.length" class="detail-card__props">
            <tr v-for="[k, v] in detail.properties" :key="k">
              <td class="supporting" style="color:var(--color-text-tertiary)">{{ k }}</td>
              <td class="supporting mono" style="word-break:break-all">{{ v }}</td>
            </tr>
          </table>
          <div v-else class="supporting" style="color:var(--color-text-tertiary);padding:8px 0">
            点击画布空白处关闭
          </div>
        </div>
      </Transition>
    </div>

    <!-- 图例 -->
    <div class="graph-overview__legend">
      <span class="legend-label supporting" style="color:var(--color-text-tertiary)">节点：</span>
      <span class="legend-item"><i style="background:#3B82F6"></i>知识点</span>
      <span class="legend-item"><i style="background:#F97316"></i>实体</span>
      <span class="legend-item"><i style="background:#EAB308"></i>分类</span>
      <span class="legend-item"><i style="background:#EC4899"></i>学生</span>
      <span class="legend-item"><i style="background:#8B5CF6"></i>考试</span>
      <span class="legend-item"><i style="background:#10B981"></i>文档</span>
      <span class="legend-item"><i style="background:#06B6D4"></i>学科</span>
    </div>
    <div class="graph-overview__legend">
      <span class="legend-label supporting" style="color:var(--color-text-tertiary)">边：</span>
      <span class="legend-item"><i class="edge-line" style="border-color:#F97316"></i>ALIGNED_TO 实体对齐</span>
      <span class="legend-item"><i class="edge-line" style="border-color:#3B82F6"></i>PREREQUISITE_OF 前置依赖</span>
      <span class="legend-item"><i class="edge-line" style="border-color:#EC4899"></i>MASTERS 掌握度</span>
      <span class="legend-item"><i class="edge-line" style="border-color:#8B5CF6"></i>TESTED 考试考查</span>
      <span class="legend-item"><i class="edge-line" style="border-color:#10B981"></i>BELONGS_TO 归属</span>
      <span class="legend-item"><i class="edge-line" style="border-color:#06B6D4"></i>BELONGS_TO_SUBJECT 学科</span>
      <span class="legend-item"><i class="edge-line" style="border-color:#EAB308"></i>CHILD_OF 层级</span>
    </div>
  </div>
</template>

<style scoped>
/* (existing styles unchanged) */
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
  position: fixed; inset: 0; z-index: 9999;
  border-radius: 0; padding: var(--spacing-md); background: var(--color-bg);
}

.graph-overview__header { display: flex; align-items: center; justify-content: space-between; }
.graph-overview__actions { display: flex; align-items: center; gap: var(--spacing-sm); }

.btn-fullscreen {
  display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px;
  border: 1px solid var(--color-border); border-radius: var(--rounded-sm);
  background: var(--color-surface); color: var(--color-text-secondary);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out),
              border-color var(--duration-fast) var(--ease-out);
}
.btn-fullscreen:hover { color: var(--color-brand); border-color: var(--color-border-focus); }

.graph-overview__body { position: relative; }
.graph-overview__canvas { width: 100%; }

.graph-overview__overlay {
  position: absolute; inset: 0; z-index: 2;
  display: flex; align-items: center; justify-content: center;
  background: var(--color-surface);
}

/* ── 详情面板 ── */
.detail-card {
  position: absolute; bottom: 12px; right: 12px;
  width: 320px; max-height: 360px; overflow-y: auto;
  background: var(--color-surface); border: 1px solid var(--color-border);
  border-radius: var(--rounded-lg); padding: var(--spacing-md);
  box-shadow: var(--shadow-card-lifted); z-index: 10;
}
.detail-card__header {
  display: flex; align-items: flex-start; justify-content: space-between;
  margin-bottom: var(--spacing-sm);
}
.detail-card__title-row { display: flex; align-items: center; gap: 6px; }
.detail-card__dot {
  width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0;
}
.detail-card__close {
  display: flex; align-items: center; justify-content: center;
  width: 24px; height: 24px; border: none; background: none;
  color: var(--color-text-tertiary); cursor: pointer; border-radius: var(--rounded-sm);
}
.detail-card__close:hover { background: var(--color-brand-veil); color: var(--color-text-primary); }
.detail-card__meta { margin-bottom: var(--spacing-sm); }
.detail-tag {
  display: inline-block; padding: 1px 8px;
  background: var(--color-brand-veil); color: var(--color-brand);
  border-radius: var(--rounded-full); font-size: 0.6875rem; font-weight: 500;
}
.detail-tag--edge {
  background: oklch(0.65 0.15 85 / 0.12); color: oklch(0.65 0.15 85);
}
.detail-card__props { width: 100%; border-collapse: collapse; }
.detail-card__props td {
  padding: 3px 8px; border-bottom: 1px solid var(--color-border); vertical-align: top;
  font-size: 0.8125rem;
}
.detail-card__props td:first-child {
  width: 40%; color: var(--color-text-tertiary); white-space: nowrap;
}

/* transition */
.slide-up-enter-active { transition: all 250ms var(--ease-out); }
.slide-up-leave-active { transition: all 150ms var(--ease-out); }
.slide-up-enter-from { opacity: 0; transform: translateY(12px); }
.slide-up-leave-to { opacity: 0; transform: translateY(12px); }

/* ── 图例 ── */
.graph-overview__legend {
  display: flex; gap: var(--spacing-md); flex-wrap: wrap;
  padding-top: var(--spacing-sm); border-top: 1px solid var(--color-border);
}
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 0.75rem; color: var(--color-text-secondary); }
.legend-item i { display: inline-block; width: 10px; height: 10px; border-radius: 50%; }
.legend-label { margin-right: 2px; }
.legend-item i.edge-line { width: 16px; height: 0; border-radius: 0; border-top: 2.5px solid; vertical-align: middle; }
</style>