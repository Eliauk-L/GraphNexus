<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getFullGraph } from '@/api/graph'
import { toGraphData } from '@/views/graph/graphAdapter'
import type { G6GraphData } from '@/views/graph/graphAdapter'
import type { GraphSubgraphVO } from '@/api/types'
import { Graph } from '@antv/g6'
import GraphCanvas from '@/views/graph/components/GraphCanvas.vue'
import GraphLegend from '@/views/graph/components/GraphLegend.vue'
import NodeDetailPanel from '@/views/graph/components/NodeDetailPanel.vue'
import { NODE_COLORS } from '@/views/graph/constants'

const graphData = ref<G6GraphData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const selectedNode = ref<{ id: string; data: Record<string, unknown> } | null>(null)
const canvasRef = ref<any>(null)
const graphInstance = ref<Graph | null>(null)
const allNodeTypes = ref<string[]>([])
const allEdgeTypes = ref<string[]>([])
const stats = ref<{ totalNodes: number; totalEdges: number; nodeCounts: Record<string, number> } | null>(null)

async function loadGraph() {
  loading.value = true
  error.value = null
  try {
    const data: GraphSubgraphVO = await getFullGraph()
    const g6Data = toGraphData(data, { full: true })
    if (!g6Data || !g6Data.nodes.length) {
      error.value = '暂无图谱数据'
      loading.value = false
      return
    }
    graphData.value = g6Data

    // 收集类型统计
    const nodeCounts: Record<string, number> = {}
    const nodeTypes = new Set<string>()
    const edgeTypes = new Set<string>()
    g6Data.nodes.forEach(n => {
      const t = (n.data.nodeType as string) ?? 'Unknown'
      nodeCounts[t] = (nodeCounts[t] ?? 0) + 1
      nodeTypes.add(t)
    })
    g6Data.edges.forEach(e => {
      edgeTypes.add((e.data.edgeType as string) ?? (e.data.type as string) ?? 'Unknown')
    })
    allNodeTypes.value = [...nodeTypes]
    allEdgeTypes.value = [...edgeTypes]
    stats.value = { totalNodes: g6Data.nodes.length, totalEdges: g6Data.edges.length, nodeCounts }
  } catch (e: any) {
    error.value = e?.message ?? '图谱加载失败'
  } finally {
    loading.value = false
  }
}

function handleCanvasReady(g: Graph) { graphInstance.value = g }
function handleNodeClick(nodeId: string, nodeData: Record<string, unknown>) {
  selectedNode.value = { id: nodeId, data: nodeData }
}
function closeDetail() { selectedNode.value = null }

// ── 类型筛选 ──
function handleNodeFilter(types: string[]) {
  const g = graphInstance.value
  const d = graphData.value
  if (!g || !d) return
  const ntSet = new Set(types)
  g.updateNodeData(d.nodes.map(n => ({
    id: n.id,
    style: { visibility: ntSet.has(n.data.nodeType as string) ? 'visible' : 'hidden' as const },
  })))
  g.draw()
}

function handleEdgeFilter(types: string[]) {
  const g = graphInstance.value
  const d = graphData.value
  if (!g || !d) return
  const etSet = new Set(types)
  g.updateEdgeData(d.edges.map(e => ({
    id: e.id,
    style: { visibility: etSet.has((e.data.edgeType ?? e.data.type) as string) ? 'visible' : 'hidden' as const },
  })))
  g.draw()
}

onMounted(loadGraph)
</script>

<template>
  <div class="graph-section">
    <div class="graph-section__header">
      <h3 class="title" style="margin:0">全量图谱可视化</h3>
      <div v-if="stats" class="graph-stats">
        <span class="stats-item">节点 {{ stats.totalNodes }}</span>
        <span class="stats-sep">·</span>
        <span class="stats-item">边 {{ stats.totalEdges }}</span>
        <template v-for="(count, type) in stats.nodeCounts" :key="'n-' + type">
          <span class="stats-sep">·</span>
          <span class="stats-item" :style="{ color: NODE_COLORS[type] ?? '#8C8C8C' }">● {{ type }} {{ count }}</span>
        </template>
      </div>
    </div>

    <div class="graph-body">
      <div class="graph-canvas-area">
        <GraphCanvas
          ref="canvasRef"
          :data="graphData"
          :loading="loading"
          :error="error"
          @node-click="handleNodeClick"
          @ready="handleCanvasReady"
        />
      </div>

      <GraphLegend
        v-if="graphData"
        :node-types="allNodeTypes"
        :edge-types="allEdgeTypes"
        @update:node-filter="() => {}"
        @update:edge-filter="() => {}"
      />

      <NodeDetailPanel
        :node="selectedNode"
        :visible="selectedNode !== null"
        @close="closeDetail"
      />
    </div>
  </div>
</template>

<style scoped>
.graph-section {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-lg);
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.graph-section__header { display: flex; align-items: center; justify-content: space-between; }

.graph-body { position: relative; }

.graph-canvas-area {
  height: 500px;
  display: flex;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
}

.graph-stats { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.stats-item { font-size: 0.75rem; color: var(--color-text-secondary); }
.stats-sep { color: var(--color-text-tertiary); font-size: 0.75rem; }
</style>