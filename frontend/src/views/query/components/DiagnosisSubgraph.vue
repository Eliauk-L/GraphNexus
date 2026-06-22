<script setup lang="ts">
/**
 * DiagnosisSubgraph — 诊断子图可视化组件（纯 SVG）。
 * 见 DESIGN § D1-D4 + UI-DESIGN § 6。
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
const W = 600
const H = 400

// ── 掌握度色阶（复用 tokens.css 变量）──
function masteryFill(weight: number | undefined): string {
  if (weight === undefined || weight === null) return 'var(--mastery-gray)'
  if (weight < 0.4) return 'var(--color-error)'
  if (weight < 0.6) return 'var(--mastery-orange)'
  if (weight < 0.8) return 'var(--mastery-yellow)'
  return 'var(--color-success)'
}

function nodeRadius(weight: number | undefined): number {
  if (weight === undefined || weight === null) return 16
  return 12 + weight * 28
}

// ── 力导向布局 ──
interface LayoutNode {
  id: string
  nodeType: string
  label: string
  weight?: number
  examHistory?: string
  x: number
  y: number
  vx: number
  vy: number
  fixed: boolean
}

interface LayoutEdge {
  source: string
  target: string
  edgeType: string
  weight: number
}

interface LayoutResult {
  nodes: LayoutNode[]
  edges: LayoutEdge[]
}

function computeLayout(data: SubgraphResponse): LayoutResult {
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

  // Student 固定居中上方
  const student = nodes.find((n) => n.nodeType === 'Student')
  if (student) {
    student.x = W / 2
    student.y = 60
  }

  const edgeMap: LayoutEdge[] = data.edges.map((e) => ({
    source: e.sourceNodeId,
    target: e.targetNodeId,
    edgeType: e.edgeType,
    weight: e.weight,
  }))

  // 力模拟（50 次迭代）
  const REPULSION = 3000
  const ATTRACTION = 0.005
  const DAMPING = 0.85
  const CENTER_GRAVITY = 0.01

  for (let iter = 0; iter < 50; iter++) {
    // 节点间斥力
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

    // 边引力
    for (const e of edgeMap) {
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

    // 中心引力 + 更新位置
    for (const n of nodes) {
      if (n.fixed) continue
      n.vx += (W / 2 - n.x) * CENTER_GRAVITY
      n.vy += (H / 2 - n.y) * CENTER_GRAVITY
      n.vx *= DAMPING
      n.vy *= DAMPING
      n.x += n.vx
      n.y += n.vy
      // 边界
      n.x = Math.max(20, Math.min(W - 20, n.x))
      n.y = Math.max(20, Math.min(H - 20, n.y))
    }
  }

  return { nodes, edges: edgeMap }
}

const layout = computed<LayoutResult | null>(() => {
  if (!subgraph.value || subgraph.value.nodes.length === 0) return null
  return computeLayout(subgraph.value)
})

// ── 唯一 ID 生成（SVG marker 等）──
let idCounter = 0
function uid(): string { return `ds-${idCounter++}` }

// ── 数据加载 ──
async function load() {
  if (!props.taskId) return
  state.value = 'loading'
  try {
    subgraph.value = await getPrunedSubgraph(props.taskId)
    state.value = 'loaded'
  } catch (e: any) {
    state.value = 'error'
    console.error('[DiagnosisSubgraph] load failed:', props.taskId, e?.message || e)
  }
}

watch(() => props.taskId, (newId) => {
  if (newId) load()
})

onMounted(() => {
  if (props.taskId) load()
})

// ── 事件处理 ──
function handleNodeClick(ln: LayoutNode) {
  emit('node-click', {
    id: ln.id,
    label: ln.label,
    weight: ln.weight,
    examHistory: ln.examHistory,
  })
}
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
      <svg
        xmlns="http://www.w3.org/2000/svg"
        :viewBox="`0 0 ${W} ${H}`"
        width="100%"
        height="400"
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
            :stroke="e.edgeType === 'MASTERS' ? 'var(--color-text-tertiary)' : 'var(--color-border)'"
            :stroke-width="e.edgeType === 'MASTERS' ? 1.5 + e.weight : 1"
            :stroke-dasharray="e.edgeType === 'PREREQUISITE_OF' ? '4,4' : 'none'"
          />
        </g>

        <!-- 节点 -->
        <g class="nodes">
          <g
            v-for="ln in layout.nodes"
            :key="ln.id"
            class="node-group"
            @click="handleNodeClick(ln)"
          >
            <circle
              :cx="ln.x"
              :cy="ln.y"
              :r="nodeRadius(ln.weight)"
              :fill="ln.nodeType === 'Student' ? 'var(--color-brand)' : masteryFill(ln.weight)"
              stroke="var(--color-surface)"
              stroke-width="2"
              class="node-circle"
            />
            <text
              :x="ln.x"
              :y="ln.y + nodeRadius(ln.weight) + 14"
              text-anchor="middle"
              font-family="var(--font-display)"
              font-size="11"
              fill="var(--color-text-primary)"
            >{{ ln.label }}</text>
          </g>
        </g>
      </svg>

      <!-- 图例 -->
      <div class="legend">
        <div class="legend-item">
          <span class="legend-dot" style="background: var(--color-error)" />
          <span class="supporting">&lt; 0.4</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot" style="background: var(--mastery-orange)" />
          <span class="supporting">0.4–0.6</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot" style="background: var(--mastery-yellow)" />
          <span class="supporting">0.6–0.8</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot" style="background: var(--color-success)" />
          <span class="supporting">≥ 0.8</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot" style="background: var(--mastery-gray)" />
          <span class="supporting">未考查</span>
        </div>
      </div>
    </div>
  </BaseCard>
</template>

<style scoped>
.subgraph-wrapper {
  position: relative;
}

.subgraph-svg {
  display: block;
}

.state-placeholder {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.skeleton-box {
  width: 100%;
  height: 400px;
  background: var(--color-border);
  border-radius: var(--rounded-md);
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* 节点交互 */
.node-group {
  cursor: pointer;
}

.node-circle {
  transition: stroke-width var(--duration-fast) var(--ease-out),
              filter var(--duration-fast) var(--ease-out);
}

.node-circle:hover {
  stroke-width: 3;
  stroke: var(--color-brand-veil);
}

/* 图例 */
.legend {
  display: flex;
  gap: var(--spacing-md);
  justify-content: flex-end;
  margin-top: var(--spacing-sm);
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: var(--rounded-full);
  flex-shrink: 0;
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-box {
    animation: none;
  }
}
</style>