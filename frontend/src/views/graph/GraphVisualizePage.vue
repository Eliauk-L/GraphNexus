<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useGraphStore } from './graphStore'
import type { GraphViewMode } from './graphStore'
import { toGraphData } from './graphAdapter'
import type { G6GraphData } from './graphAdapter'
import { useGraphInteraction } from './composables/useGraphInteraction'
import BaseSelect from '@/common/components/BaseSelect.vue'
import BaseCard from '@/common/components/BaseCard.vue'
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

function refreshGraphData() {
  if (store.currentGraph) {
    graphData.value = toGraphData(store.currentGraph)
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

// ── 视图模式切换 ──

async function handleViewModeChange(mode: GraphViewMode) {
  selectedNode.value = null
  ctrlClickedNodeId.value = null
  graphData.value = null

  if (mode === 'full') {
    await store.loadFullGraph()
  } else if (selectedDocId.value) {
    await store.loadDocumentSubgraph(selectedDocId.value)
  }
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
  await store.loadDocuments()
  const docId = route.params.id
  if (docId) {
    selectedDocId.value = Number(docId)
    await store.loadDocumentSubgraph(Number(docId))
  }
})
</script>

<template>
  <div>
    <GraphToolbar
      :view-mode="store.viewMode"
      :search-results="searchResults"
      @update:view-mode="handleViewModeChange"
      @search="handleSearch"
      @select-node="handleSelectNode"
    />

    <!-- 文档选择器（仅文档子图模式） -->
    <div v-if="store.viewMode === 'document'" style="margin-bottom: var(--spacing-md);">
      <BaseSelect
        :model-value="selectedDocId"
        :options="docOptions"
        placeholder="选择一个已抽取的文档"
        style="width: 320px"
        @update:model-value="(v) => handleDocSelect(v as number)"
      />
    </div>

    <BaseCard>
      <template #header>
        <div class="graph-controls" />
      </template>

      <GraphCanvas
        ref="canvasRef"
        :data="graphData"
        :loading="store.loading"
        :error="store.error"
        @node-click="handleNodeClick"
        @node-ctrl-click="handleNodeCtrlClick"
        @ready="handleCanvasReady"
      />

      <GraphLegend
        v-if="graphData"
        :node-types="interaction.allNodeTypes.value"
        :edge-types="interaction.allEdgeTypes.value"
        @update:node-filter="handleNodeFilter"
        @update:edge-filter="handleEdgeFilter"
      />
    </BaseCard>

    <NodeDetailPanel
      :node="selectedNode"
      :visible="selectedNode !== null"
      @close="selectedNode = null; interaction.clearHighlight()"
      @expand-neighbors="handleExpandNeighbors"
    />
  </div>
</template>

<style scoped>
.graph-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
</style>