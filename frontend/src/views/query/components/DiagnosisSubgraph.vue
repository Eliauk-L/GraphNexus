<script setup lang="ts">
/**
 * DiagnosisSubgraph — 知识结构子图可视化组件（G6 v5 动态力导向）。
 * 展示学生→知识点的掌握关系（MASTERS）和知识点间的前置依赖（PREREQUISITE_OF）。
 * 参考 GraphCanvas 的 G6 生命周期管理。
 */
import { ref, watch, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { Graph } from '@antv/g6'
import { getPrunedSubgraph } from '@/api/analysis'
import type { SubgraphResponse, SubgraphNodeVO, SubgraphEdgeVO } from '@/api/types'
import BaseCard from '@/common/components/BaseCard.vue'
import { NODE_COLORS, NODE_SIZES, EDGE_COLORS, EDGE_LINE_STYLES } from '@/views/graph/constants'

const props = defineProps<{
  taskId: string | null
}>()

const emit = defineEmits<{
  'node-click': [node: { id: string; label: string; weight?: number; examHistory?: string }]
}>()

// ── 状态 ──
type LoadState = 'idle' | 'loading' | 'loaded' | 'error'
const state = ref<LoadState>('idle')
const subgraph = ref<SubgraphResponse | null>(null)

// ── G6 ──
const container = ref<HTMLDivElement>()
let graph: Graph | null = null
let resizeObserver: ResizeObserver | null = null

// ── 掌握度色阶 ──
function masteryColor(weight: number | undefined): string {
  if (weight === undefined || weight === null) return '#9CA3AF'
  if (weight < 0.4) return '#EF4444'
  if (weight < 0.6) return '#F59E0B'
  if (weight < 0.8) return '#EAB308'
  return '#10B981'
}

function kpNodeSize(weight: number | undefined): number {
  return weight !== undefined ? 22 + weight * 36 : 28
}

// ── 数据转换 ──
function toG6Data(data: SubgraphResponse) {
  const nodes = data.nodes.map((n) => {
    const isKp = n.nodeType === 'KnowledgePoint'
    const weight = isKp && typeof n.properties?.weight === 'number' ? n.properties.weight as number : undefined
    const label = (n.properties?.name as string) ?? (n.properties?.label as string) ?? n.id.substring(0, 8)
    return {
      id: n.id,
      data: {
        label,
        nodeType: n.nodeType,
        color: n.nodeType === 'Student' ? NODE_COLORS.Student : masteryColor(weight),
        size: n.nodeType === 'Student' ? NODE_SIZES.Student : kpNodeSize(weight),
        weight,
        examHistory: n.properties?.examHistory,
      },
    }
  })

  const edges = data.edges.map((e, i) => ({
    id: `e-${i}`,
    source: e.sourceNodeId,
    target: e.targetNodeId,
    data: {
      type: e.edgeType,
      color: EDGE_COLORS[e.edgeType] ?? '#9CA3AF',
      width: e.edgeType === 'MASTERS' ? 2 + (e.weight ?? 0) * 2 : 2,
      lineStyle: EDGE_LINE_STYLES[e.edgeType] ?? 'solid',
      weight: e.weight,
      label: e.edgeType === 'PREREQUISITE_OF' && e.weight != null
        ? `依赖${e.weight.toFixed(2)}` : e.edgeType,
    },
  }))

  return { nodes, edges }
}

// ── G6 图创建 ──
async function createGraph() {
  if (!container.value || !subgraph.value) return

  const rect = container.value.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) {
    setTimeout(() => { if (subgraph.value) createGraph() }, 100)
    return
  }

  if (graph) { graph.destroy(); graph = null }

  const g6Data = toG6Data(subgraph.value)

  try {
    graph = new Graph({
      container: container.value,
      width: rect.width,
      height: rect.height,
      data: g6Data,
      autoFit: 'view' as const,
      node: {
        type: 'circle',
        style: {
          size: (d: any) => d.data?.size ?? 28,
          fill: (d: any) => d.data?.color ?? '#9CA3AF',
          stroke: '#fff',
          lineWidth: 2,
          labelText: (d: any) => d.data?.label ?? '',
          labelFontSize: 11,
          labelFill: '#374151',
          labelPlacement: 'bottom',
          labelOffsetY: 6,
        },
        state: {
          hover: { lineWidth: 3, stroke: 'rgba(59,130,246,0.4)' },
        },
      },
      edge: {
        type: 'line',
        style: {
          stroke: (d: any) => d.data?.color ?? '#9CA3AF',
          lineWidth: (d: any) => d.data?.width ?? 2,
          opacity: 0.7,
          endArrow: true,
          lineDash: (d: any) => {
            const ls = d.data?.lineStyle
            if (ls === 'dashed') return [8, 4]
            if (ls === 'dotted') return [2, 4]
            return undefined
          },
          labelText: (d: any) => d.data?.label ?? '',
          labelFontSize: 10,
          labelFill: '#555',
          labelBackground: true,
          labelBackgroundFill: '#fff',
          labelBackgroundOpacity: 0.8,
          labelBackgroundPadding: [2, 4],
        },
      },
      layout: {
        type: 'd3-force',
        animate: true,
        nodeStrength: (d: any) => d.data?.nodeType === 'Student' ? -20000 : -6000,
        linkDistance: 140,
        gravity: 0.3,
        maxIterations: 300,
      },
      behaviors: [
        'drag-canvas',
        'zoom-canvas',
        { type: 'hover-activate', degree: 1, state: 'hover' },
      ],
    })

    graph.on('node:click', (evt: any) => {
      const nodeId = evt.target?.id
      if (!nodeId) return
      const nodeData = subgraph.value?.nodes.find((n) => n.id === nodeId)
      if (nodeData) {
        emit('node-click', {
          id: nodeId,
          label: (nodeData.properties?.name as string) ?? (nodeData.properties?.label as string) ?? nodeId.substring(0, 8),
          weight: typeof nodeData.properties?.weight === 'number' ? nodeData.properties.weight as number : undefined,
          examHistory: typeof nodeData.properties?.examHistory === 'string' ? nodeData.properties.examHistory as string : undefined,
        })
      }
    })

    await graph.render()
  } catch (err) {
    console.error('[DiagnosisSubgraph] G6 create failed:', err)
    graph = null
  }
}

// ── 数据加载 ──
async function load() {
  if (!props.taskId) return
  state.value = 'loading'
  try {
    subgraph.value = await getPrunedSubgraph(props.taskId)
    state.value = 'loaded'
    await nextTick()
    await createGraph()
  } catch (e: any) {
    state.value = 'error'
    console.error('[DiagnosisSubgraph] load failed:', props.taskId, e?.message || e)
  }
}

watch(() => props.taskId, (newId) => { if (newId) load() })
onMounted(() => { if (props.taskId) load() })

// ── ResizeObserver ──
onMounted(() => {
  if (container.value) {
    resizeObserver = new ResizeObserver(() => {
      if (graph && container.value) {
        const r = container.value.getBoundingClientRect()
        if (r.width > 0 && r.height > 0) {
          try { graph.setSize(r.width, r.height) } catch { /* ignore */ }
        }
      }
    })
    resizeObserver.observe(container.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
  if (graph) { graph.destroy(); graph = null }
})

// ── 统计 ──
const stats = computed(() => {
  if (!subgraph.value) return null
  const kpNodes = subgraph.value.nodes.filter(n => n.nodeType === 'KnowledgePoint')
  const mastersEdges = subgraph.value.edges.filter(e => e.edgeType === 'MASTERS')
  const prereqEdges = subgraph.value.edges.filter(e => e.edgeType === 'PREREQUISITE_OF')
  return { kpCount: kpNodes.length, mastersCount: mastersEdges.length, prereqCount: prereqEdges.length }
})
</script>

<template>
  <BaseCard title="知识结构子图">
    <!-- loading -->
    <div v-if="state === 'loading'" class="state-placeholder">
      <div class="skeleton-box" />
    </div>

    <!-- error -->
    <div v-else-if="state === 'error'" class="state-placeholder supporting" style="color: var(--color-text-tertiary)">
      子图数据加载失败
    </div>

    <!-- empty -->
    <div v-else-if="state === 'loaded' && subgraph && subgraph.nodes.length === 0" class="state-placeholder supporting" style="color: var(--color-text-tertiary)">
      暂无子图数据
    </div>

    <!-- G6 画布 -->
    <div v-else-if="state === 'loaded' && subgraph" class="subgraph-wrapper">
      <!-- 统计摘要 -->
      <div v-if="stats" class="stats-bar supporting">
        <span>{{ stats.kpCount }} 个知识点</span>
        <span class="stats-sep">|</span>
        <span>{{ stats.mastersCount }} 条掌握关系</span>
        <span class="stats-sep">|</span>
        <span>{{ stats.prereqCount }} 条前置依赖</span>
      </div>

      <div ref="container" class="g6-container" />

      <!-- 图例 -->
      <div class="legend">
        <div class="legend-section">
          <span class="legend-title">掌握度</span>
          <div class="legend-item"><span class="legend-dot" style="background: #EF4444" /><span class="supporting">&lt;40%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #F59E0B" /><span class="supporting">40-60%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #EAB308" /><span class="supporting">60-80%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #10B981" /><span class="supporting">≥80%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #9CA3AF" /><span class="supporting">未考查</span></div>
        </div>
        <div class="legend-section">
          <span class="legend-title">关系类型</span>
          <div class="legend-item"><span class="legend-line legend-line--masters" /><span class="supporting">掌握关系</span></div>
          <div class="legend-item"><span class="legend-line legend-line--prereq" /><span class="supporting">前置依赖</span></div>
        </div>
        <div class="legend-section">
          <span class="legend-title">节点类型</span>
          <div class="legend-item"><span class="legend-dot" :style="{ background: NODE_COLORS.Student }" /><span class="supporting">学生</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #3B82F6" /><span class="supporting">知识点</span></div>
        </div>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.subgraph-wrapper { position: relative; }

.stats-bar {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  background: var(--color-bg);
  border-radius: var(--rounded-sm);
  color: var(--color-text-secondary);
  justify-content: center;
}
.stats-sep { color: var(--color-border); }

.g6-container {
  width: 100%;
  height: 440px;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  overflow: hidden;
  background: var(--color-surface);
}

.state-placeholder {
  height: 440px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.skeleton-box {
  width: 100%;
  height: 440px;
  background: var(--color-border);
  border-radius: var(--rounded-md);
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* 图例 */
.legend {
  display: flex;
  gap: var(--spacing-xl);
  justify-content: center;
  margin-top: var(--spacing-md);
  flex-wrap: wrap;
}

.legend-section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.legend-title {
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 2px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: var(--rounded-full);
  flex-shrink: 0;
}

.legend-line {
  width: 24px;
  height: 2px;
  flex-shrink: 0;
  border-radius: 1px;
}

.legend-line--masters { background: #EC4899; }

.legend-line--prereq {
  background: repeating-linear-gradient(90deg, #3B82F6 0px, #3B82F6 5px, transparent 5px, transparent 8px);
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-box { animation: none; }
}
</style>