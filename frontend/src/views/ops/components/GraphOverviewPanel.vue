<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { NSpin } from 'naive-ui'
import { getFullGraph } from '@/api/graph'
import { toGraphData } from '@/views/graph/graphAdapter'
import type { GraphSubgraphVO } from '@/api/types'
import { Graph } from '@antv/g6'

const loading = ref(false)
const error = ref<string | null>(null)
const containerRef = ref<HTMLDivElement>()
let graph: Graph | null = null

// 加载并渲染全量图谱
async function loadGraph() {
  if (!containerRef.value) return
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

    // 销毁旧图
    if (graph) { graph.destroy(); graph = null }

    await nextTick()

    const width = containerRef.value.clientWidth
    const height = containerRef.value.clientHeight

    graph = new Graph({
      container: containerRef.value,
      width,
      height,
      data: {
        nodes: g6Data.nodes.map(n => ({
          id: n.id,
          data: { ...n.data },
        })),
        edges: g6Data.edges.map(e => ({
          id: e.id,
          source: e.source,
          target: e.target,
          data: { ...e.data },
        })),
      },
      layout: {
        type: 'force',
        preventOverlap: true,
        nodeStrength: -200,
        edgeStrength: 0.1,
      },
      autoFit: 'view',
      animation: true,
      behaviors: ['zoom-canvas', 'drag-canvas', 'drag-element'],
    })

    graph.render()
  } catch (e: any) {
    error.value = e?.message ?? '图谱加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadGraph)

onBeforeUnmount(() => {
  if (graph) { graph.destroy(); graph = null }
})
</script>

<template>
  <div class="graph-overview">
    <div class="graph-overview__header">
      <h3 class="title" style="margin:0">全量图谱可视化</h3>
      <span class="supporting" style="color:var(--color-text-tertiary)">Neo4j 全部节点和边</span>
    </div>

    <div class="graph-overview__body">
      <NSpin v-if="loading" style="height:100%;display:flex;align-items:center;justify-content:center" />
      <div v-else-if="error" class="graph-overview__empty supporting" style="color:var(--color-text-tertiary)">
        {{ error }}
      </div>
      <div v-else ref="containerRef" class="graph-overview__canvas" />
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

.graph-overview__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.graph-overview__body {
  height: 560px;
  position: relative;
}

.graph-overview__canvas {
  width: 100%;
  height: 100%;
}

.graph-overview__empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>