<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGraphStore } from './graphStore'
import { toGraphData } from './graphAdapter'
import type { G6GraphData } from './graphAdapter'
import { useGraphInteraction } from './composables/useGraphInteraction'
import BaseSelect from '@/common/components/BaseSelect.vue'
import GraphCanvas from './components/GraphCanvas.vue'
import GraphToolbar from './components/GraphToolbar.vue'
import GraphLegend from './components/GraphLegend.vue'
import NodeDetailPanel from './components/NodeDetailPanel.vue'

const store = useGraphStore()
const route = useRoute()
const selectedNode = ref<{ id: string; data: Record<string, unknown> } | null>(null)
const selectedDocId = ref<number | null>(null)
const ctrlClickedNodeId = ref<string | null>(null)
const searchResults = ref<{ id: string; label: string; nodeType: string }[]>([])

const graphData = ref<G6GraphData | null>(null)
const canvasRef = ref<any>(null)
const graphInstance = ref<any>(null)
const graphContainer = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)

function refreshGraphData() {
  if (store.currentGraph) {
    graphData.value = toGraphData(store.currentGraph)
    console.debug('[GraphViz] graphData:', graphData.value.nodes.length, 'nodes,', graphData.value.edges.length, 'edges')
  } else {
    graphData.value = null
  }
}

watch(() => store.currentGraph, () => {
  refreshGraphData()
})

const interaction = useGraphInteraction(
  () => canvasRef.value?.getGraph?.() ?? null,
  () => graphData.value,
)

// ── 全屏切换 ──

async function toggleFullscreen() {
  if (!graphContainer.value) return
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await graphContainer.value.requestFullscreen()
    }
  } catch (e) {
    console.warn('[GraphViz] fullscreen error:', e)
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
  setTimeout(() => {
    const g = canvasRef.value?.getGraph?.()
    if (g) {
      try { g.render() } catch { /* ignore */ }
    }
  }, 300)
}

// ── 文档选择 → 子图 ──

const docOptions = computed(() =>
  store.documents.map((d) => ({ label: `${d.name} (ID: ${d.documentId})`, value: d.documentId }))
)

async function handleDocSelect(docId: number) {
  selectedDocId.value = docId
  selectedNode.value = null
  ctrlClickedNodeId.value = null
  await store.loadDocumentSubgraph(docId)
}

// ── 搜索 ──

function handleSearch(query: string) {
  searchResults.value = interaction.search(query)
}

function handleSelectNode(nodeId: string) {
  interaction.highlightNode(nodeId)
  interaction.focusNode(nodeId)
  searchResults.value = []

  const data = graphData.value
  if (data) {
    const node = data.nodes.find((n) => n.id === nodeId)
    if (node) {
      selectedNode.value = { id: node.id, data: { ...node.data } }
    }
  }
}

// ── 节点点击 ──

function handleNodeClick(nodeId: string, nodeData: Record<string, unknown>) {
  ctrlClickedNodeId.value = null
  selectedNode.value = { id: nodeId, data: nodeData }

  if (interaction.state.value.pathSourceId) {
    interaction.clearHighlight()
  }
  interaction.highlightNode(nodeId)
}

function handleNodeCtrlClick(nodeId: string) {
  const currentSelected = interaction.state.value.selectedNodeId
  if (currentSelected && currentSelected !== nodeId) {
    interaction.highlightPath(currentSelected, nodeId)
    ctrlClickedNodeId.value = nodeId
  }
}

// ── 邻域展开 ──

function handleExpandNeighbors() {
  if (selectedNode.value) {
    interaction.expandNeighbors(selectedNode.value.id)
  }
}

// ── 图例筛选 ──

function handleNodeFilter(types: string[]) {
  const edgeTypes = interaction.allEdgeTypes.value
  interaction.filterByTypes(types, edgeTypes)
}

function handleEdgeFilter(types: string[]) {
  const nodeTypes = interaction.allNodeTypes.value
  interaction.filterByTypes(nodeTypes, types)
}

function handleCanvasReady(g: any) {
  graphInstance.value = g
}

// ── Escape ──

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    interaction.clearHighlight()
    selectedNode.value = null
    ctrlClickedNodeId.value = null
  }
}

onMounted(async () => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  await store.loadDocuments()
  const docId = route.params.id
  if (docId) {
    selectedDocId.value = Number(docId)
    await store.loadDocumentSubgraph(Number(docId))
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<template>
  <div ref="graphContainer" class="graph-page">
    <div class="graph-topbar">
      <GraphToolbar
        :search-results="searchResults"
        @search="handleSearch"
        @select-node="handleSelectNode"
      />
      <button class="btn-fullscreen" :title="isFullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen">
        <svg v-if="!isFullscreen" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="15 3 21 3 21 9" />
          <polyline points="9 21 3 21 3 15" />
          <line x1="21" y1="3" x2="14" y2="10" />
          <line x1="3" y1="21" x2="10" y2="14" />
        </svg>
        <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="4 8 4 3 9 3" />
          <polyline points="20 16 20 21 15 21" />
          <line x1="4" y1="3" x2="11" y2="10" />
          <line x1="20" y1="21" x2="13" y2="14" />
        </svg>
      </button>
    </div>

    <div v-if="!isFullscreen" style="margin-bottom: var(--spacing-sm); flex-shrink: 0;">
      <BaseSelect
        :model-value="selectedDocId"
        :options="docOptions"
        placeholder="选择一个已抽取的文档"
        style="width: 320px"
        @update:model-value="(v) => handleDocSelect(v as number)"
      />
    </div>

    <div class="graph-body">
      <div class="graph-canvas-area">
        <GraphCanvas
          ref="canvasRef"
          :data="graphData"
          :loading="store.loading"
          :error="store.error"
          :fullscreen="isFullscreen"
          @node-click="handleNodeClick"
          @node-ctrl-click="handleNodeCtrlClick"
          @ready="handleCanvasReady"
        />
      </div>

      <GraphLegend
        v-if="graphData"
        :node-types="interaction.allNodeTypes.value"
        :edge-types="interaction.allEdgeTypes.value"
        @update:node-filter="handleNodeFilter"
        @update:edge-filter="handleEdgeFilter"
      />
    </div>

    <NodeDetailPanel
      :node="selectedNode"
      :visible="selectedNode !== null"
      @close="selectedNode = null; interaction.clearHighlight()"
      @expand-neighbors="handleExpandNeighbors"
    />
  </div>
</template>

<style scoped>
.graph-page {
  display: flex;
  flex-direction: column;
}

.graph-body {
  display: flex;
  flex-direction: column;
}

.graph-canvas-area {
  display: flex;
  flex-direction: column;
}

.graph-topbar {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  flex-shrink: 0;
  margin-bottom: var(--spacing-sm);
}

.graph-topbar > :first-child {
  flex: 1;
}

.btn-fullscreen {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-sm);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color 150ms ease, border-color 150ms ease;
}
.btn-fullscreen:hover {
  color: var(--color-brand);
  border-color: var(--color-brand);
}
</style>

<style>
.graph-page:fullscreen {
  padding: var(--spacing-md);
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
}

.graph-page:fullscreen .graph-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.graph-page:fullscreen .graph-canvas-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>