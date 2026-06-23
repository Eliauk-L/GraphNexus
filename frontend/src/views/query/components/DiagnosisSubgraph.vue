<script setup lang="ts">
/**
 * DiagnosisSubgraph — 知识结构子图可视化组件。
 * 展示学生→知识点的掌握关系（MASTERS）和知识点间的前置依赖（PREREQUISITE_OF）。
 * 参考 GraphVisualizePage 的力导向布局 + 掌握度色阶。
 */
import { ref, watch, onMounted, computed } from 'vue'
import { getPrunedSubgraph } from '@/api/analysis'
import type { SubgraphResponse, SubgraphNodeVO, SubgraphEdgeVO } from '@/api/types'
import BaseCard from '@/common/components/BaseCard.vue'

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

// ── 画布参数 ──
const W = 640
const H = 440

// ── 掌握度色阶 ──
function masteryFill(weight: number | undefined): string {
  if (weight === undefined || weight === null) return '#9CA3AF'
  if (weight < 0.4) return '#EF4444'
  if (weight < 0.6) return '#F59E0B'
  if (weight < 0.8) return '#EAB308'
  return '#10B981'
}

function nodeRadius(weight: number | undefined): number {
  if (weight === undefined || weight === null) return 16
  return 13 + weight * 26
}

// ── 力导向布局 ──
interface LayoutNode {
  id: string
  nodeType: string
  label: string
  weight?: number
  examHistory?: string
  x: number; y: number
  vx: number; vy: number
  fixed: boolean
}

interface LayoutEdge {
  source: string
  target: string
  edgeType: string
  weight: number
}

function computeLayout(data: SubgraphResponse): { nodes: LayoutNode[]; edges: LayoutEdge[] } {
  const nodeMap = new Map(data.nodes.map(n => [n.id, n]))
  const nodes: LayoutNode[] = data.nodes.map((n) => ({
    id: n.id,
    nodeType: n.nodeType,
    label: (n.properties?.name as string) ?? (n.properties?.label as string) ?? n.id.substring(0, 8),
    weight: typeof n.properties?.weight === 'number' ? (n.properties.weight as number) : undefined,
    examHistory: typeof n.properties?.examHistory === 'string' ? (n.properties.examHistory as string) : undefined,
    x: Math.random() * (W - 100) + 50,
    y: Math.random() * (H - 100) + 50,
    vx: 0, vy: 0,
    fixed: n.nodeType === 'Student',
  }))

  // Student 固定居中偏上
  const student = nodes.find((n) => n.nodeType === 'Student')
  if (student) { student.x = W / 2; student.y = 70 }

  const edges: LayoutEdge[] = data.edges.map((e) => ({
    source: e.sourceNodeId,
    target: e.targetNodeId,
    edgeType: e.edgeType,
    weight: e.weight ?? 1.0,
  }))

  // 力模拟（60 次迭代）
  const REPULSION = 3200
  const ATTRACTION = 0.006
  const DAMPING = 0.85
  const CENTER_GRAVITY = 0.008

  for (let iter = 0; iter < 60; iter++) {
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const dx = nodes[j].x - nodes[i].x
        const dy = nodes[j].y - nodes[i].y
        const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
        const force = REPULSION / (dist * dist)
        const fx = (dx / dist) * force
        const fy = (dy / dist) * force
        if (!nodes[i].fixed) { nodes[i].vx -= fx; nodes[i].vy -= fy }
        if (!nodes[j].fixed) { nodes[j].vx += fx; nodes[j].vy += fy }
      }
    }
    for (const e of edges) {
      const s = nodes.find((n) => n.id === e.source)
      const t = nodes.find((n) => n.id === e.target)
      if (!s || !t) continue
      const dx = t.x - s.x
      const dy = t.y - s.y
      const dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1)
      const force = dist * ATTRACTION
      const fx = (dx / dist) * force
      const fy = (dy / dist) * force
      if (!s.fixed) { s.vx += fx; s.vy += fy }
      if (!t.fixed) { t.vx -= fx; t.vy -= fy }
    }
    for (const n of nodes) {
      if (n.fixed) continue
      n.vx += (W / 2 - n.x) * CENTER_GRAVITY
      n.vy += (H / 2 - n.y) * CENTER_GRAVITY
      n.vx *= DAMPING; n.vy *= DAMPING
      n.x += n.vx; n.y += n.vy
      n.x = Math.max(30, Math.min(W - 30, n.x))
      n.y = Math.max(30, Math.min(H - 30, n.y))
    }
  }

  return { nodes, edges }
}

const layout = computed(() => {
  if (!subgraph.value || subgraph.value.nodes.length === 0) return null
  return computeLayout(subgraph.value)
})

// ── 边标签位置（PREREQUISITE_OF 边中点偏移）──
function edgeLabelPos(sx: number, sy: number, tx: number, ty: number) {
  const mx = (sx + tx) / 2
  const my = (sy + ty) / 2
  return { x: mx - 8, y: my - 6 }
}

let idCounter = 0
function uid(): string { return `ds-${idCounter++}` }

// ── 数据加载 ──
async function load() {
  if (!props.taskId) return
  state.value = 'loading'
  idCounter = 0
  try {
    subgraph.value = await getPrunedSubgraph(props.taskId)
    state.value = 'loaded'
  } catch (e: any) {
    state.value = 'error'
    console.error('[DiagnosisSubgraph] load failed:', props.taskId, e?.message || e)
  }
}

watch(() => props.taskId, (newId) => { if (newId) load() })
onMounted(() => { if (props.taskId) load() })

function handleNodeClick(ln: LayoutNode) {
  emit('node-click', {
    id: ln.id,
    label: ln.label,
    weight: ln.weight,
    examHistory: ln.examHistory,
  })
}

// ── 统计 ──
const stats = computed(() => {
  if (!layout.value) return null
  const kpNodes = layout.value.nodes.filter(n => n.nodeType !== 'Student')
  const mastersEdges = layout.value.edges.filter(e => e.edgeType === 'MASTERS')
  const prereqEdges = layout.value.edges.filter(e => e.edgeType === 'PREREQUISITE_OF')
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
    <div v-else-if="state === 'loaded' && (!layout || layout.nodes.length === 0)" class="state-placeholder supporting" style="color: var(--color-text-tertiary)">
      暂无子图数据
    </div>

    <!-- 子图 SVG -->
    <div v-else-if="state === 'loaded' && layout" class="subgraph-wrapper">
      <!-- 统计摘要 -->
      <div v-if="stats" class="stats-bar supporting">
        <span>{{ stats.kpCount }} 个知识点</span>
        <span class="stats-sep">|</span>
        <span>{{ stats.mastersCount }} 条掌握关系</span>
        <span class="stats-sep">|</span>
        <span>{{ stats.prereqCount }} 条前置依赖</span>
      </div>

      <svg
        xmlns="http://www.w3.org/2000/svg"
        :viewBox="`0 0 ${W} ${H}`"
        width="100%"
        height="440"
        class="subgraph-svg"
      >
        <!-- 边 -->
        <g class="edges">
          <line
            v-for="e in layout.edges"
            :key="uid()"
            :x1="layout.nodes.find(n => n.id === e.source)?.x ?? 0"
            :y1="layout.nodes.find(n => n.id === e.source)?.y ?? 0"
            :x2="layout.nodes.find(n => n.id === e.target)?.x ?? 0"
            :y2="layout.nodes.find(n => n.id === e.target)?.y ?? 0"
            :stroke="e.edgeType === 'MASTERS' ? '#9CA3AF' : '#6B7280'"
            :stroke-width="e.edgeType === 'MASTERS' ? 1.5 + e.weight * 2 : 1.8"
            :stroke-dasharray="e.edgeType === 'PREREQUISITE_OF' ? '5,3' : 'none'"
            :opacity="e.edgeType === 'MASTERS' ? 0.6 : 0.8"
          />
          <!-- PREREQUISITE_OF 边标签（依赖强度） -->
          <text
            v-for="e in layout.edges.filter(ed => ed.edgeType === 'PREREQUISITE_OF')"
            :key="'el-' + uid()"
            :x="edgeLabelPos(
              layout.nodes.find(n => n.id === e.source)?.x ?? 0,
              layout.nodes.find(n => n.id === e.source)?.y ?? 0,
              layout.nodes.find(n => n.id === e.target)?.x ?? 0,
              layout.nodes.find(n => n.id === e.target)?.y ?? 0
            ).x"
            :y="edgeLabelPos(
              layout.nodes.find(n => n.id === e.source)?.x ?? 0,
              layout.nodes.find(n => n.id === e.source)?.y ?? 0,
              layout.nodes.find(n => n.id === e.target)?.x ?? 0,
              layout.nodes.find(n => n.id === e.target)?.y ?? 0
            ).y"
            font-size="10"
            fill="#6B7280"
            text-anchor="start"
          >{{ e.weight?.toFixed(2) }}</text>
        </g>

        <!-- 节点 -->
        <g class="nodes">
          <g
            v-for="ln in layout.nodes"
            :key="ln.id"
            class="node-group"
            @click="handleNodeClick(ln)"
          >
            <!-- 外环（hover 指示） -->
            <circle
              :cx="ln.x" :cy="ln.y"
              :r="nodeRadius(ln.weight) + 4"
              fill="none" stroke="transparent" stroke-width="2"
              class="node-ring"
            />
            <!-- 主节点 -->
            <circle
              :cx="ln.x" :cy="ln.y"
              :r="nodeRadius(ln.weight)"
              :fill="ln.nodeType === 'Student' ? '#3B82F6' : masteryFill(ln.weight)"
              stroke="#fff" stroke-width="2"
              class="node-circle"
            />
            <!-- Student 图标标签 -->
            <text
              v-if="ln.nodeType === 'Student'"
              :x="ln.x" :y="ln.y + 5"
              text-anchor="middle" font-size="12" fill="#fff" font-weight="600"
            >生</text>
            <!-- 掌握度百分比（KP 节点内） -->
            <text
              v-if="ln.nodeType !== 'Student' && ln.weight !== undefined"
              :x="ln.x" :y="ln.y + 4"
              text-anchor="middle" font-size="10" fill="#fff" font-weight="500"
            >{{ Math.round(ln.weight * 100) }}%</text>
            <!-- 节点名称标签 -->
            <text
              :x="ln.x"
              :y="ln.y + nodeRadius(ln.weight) + 16"
              text-anchor="middle"
              font-family="var(--font-body)"
              font-size="11"
              :fill="ln.nodeType === 'Student' ? '#1D4ED8' : '#374151'"
              font-weight="500"
            >{{ ln.label }}</text>
          </g>
        </g>
      </svg>

      <!-- 图例 -->
      <div class="legend">
        <div class="legend-section">
          <span class="supporting" style="font-weight: 600; color: var(--color-text-secondary)">掌握度</span>
          <div class="legend-item"><span class="legend-dot" style="background: #EF4444" /><span class="supporting">&lt;40%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #F59E0B" /><span class="supporting">40-60%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #EAB308" /><span class="supporting">60-80%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #10B981" /><span class="supporting">≥80%</span></div>
          <div class="legend-item"><span class="legend-dot" style="background: #9CA3AF" /><span class="supporting">未考查</span></div>
        </div>
        <div class="legend-section">
          <span class="supporting" style="font-weight: 600; color: var(--color-text-secondary)">关系类型</span>
          <div class="legend-item"><span class="legend-line legend-line--masters" /><span class="supporting">掌握关系</span></div>
          <div class="legend-item"><span class="legend-line legend-line--prereq" /><span class="supporting">前置依赖</span></div>
        </div>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.subgraph-wrapper { position: relative; }
.subgraph-svg { display: block; }

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

.node-group { cursor: pointer; }

.node-circle {
  transition: stroke-width var(--duration-fast) var(--ease-out);
}

.node-ring {
  transition: stroke var(--duration-fast) var(--ease-out);
}

.node-group:hover .node-ring { stroke: rgba(59, 130, 246, 0.3); }
.node-group:hover .node-circle { stroke-width: 3; }

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

.legend-line--masters { background: #9CA3AF; opacity: 0.6; }

.legend-line--prereq {
  background: repeating-linear-gradient(90deg, #6B7280 0px, #6B7280 5px, transparent 5px, transparent 8px);
  opacity: 0.8;
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-box { animation: none; }
}
</style>