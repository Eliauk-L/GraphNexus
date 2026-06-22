/**
 * useGraphInteraction — G6 图谱交互逻辑 composable。
 * 封装搜索、筛选、选中、邻域展开、路径高亮等交互模式。
 * 只使用 G6 v5 公开 API（setElementState / focusElement / getNodeData / getEdgeData / draw）。
 */
import { ref, computed } from 'vue'
import type { Graph } from '@antv/g6'
import type { G6GraphData } from '../graphAdapter'

export interface InteractionState {
  selectedNodeId: string | null
  highlightedNodeIds: Set<string>
  dimmed: boolean
  pathSourceId: string | null
  pathTargetId: string | null
  nodeFilter: Set<string>
  edgeFilter: Set<string>
}

export function useGraphInteraction(
  graphInstance: () => Graph | null,
  graphData: () => G6GraphData | null,
) {
  const state = ref<InteractionState>({
    selectedNodeId: null,
    highlightedNodeIds: new Set(),
    dimmed: false,
    pathSourceId: null,
    pathTargetId: null,
    nodeFilter: new Set(),
    edgeFilter: new Set(),
  })

  const allNodeTypes = computed(() => {
    const data = graphData()
    if (!data) return [] as string[]
    return [...new Set(data.nodes.map((n) => n.data.nodeType))]
  })

  const allEdgeTypes = computed(() => {
    const data = graphData()
    if (!data) return [] as string[]
    return [...new Set(data.edges.map((e) => e.data.type))]
  })

  const stats = computed(() => {
    const data = graphData()
    if (!data) return null
    const nodeCounts: Record<string, number> = {}
    const edgeCounts: Record<string, number> = {}
    for (const n of data.nodes) {
      nodeCounts[n.data.nodeType] = (nodeCounts[n.data.nodeType] ?? 0) + 1
    }
    for (const e of data.edges) {
      edgeCounts[e.data.type] = (edgeCounts[e.data.type] ?? 0) + 1
    }
    return {
      totalNodes: data.nodes.length,
      totalEdges: data.edges.length,
      nodeCounts,
      edgeCounts,
    }
  })

  // ── 搜索 ──

  function search(query: string): { id: string; label: string; nodeType: string }[] {
    const data = graphData()
    if (!data || query.length < 2) return []
    const q = query.toLowerCase()
    return data.nodes
      .filter((n) => {
        const label = String(n.data.label ?? '').toLowerCase()
        const id = n.id.toLowerCase()
        return label.includes(q) || id.includes(q)
      })
      .slice(0, 20)
      .map((n) => ({ id: n.id, label: String(n.data.label ?? n.id), nodeType: String(n.data.nodeType) }))
  }

  // ── 高亮节点 + dim 其余 ──

  async function highlightNode(nodeId: string) {
    const g = graphInstance()
    const data = graphData()
    if (!g || !data) return

    const stateMap: Record<string, string | string[]> = {}
    for (const n of data.nodes) {
      stateMap[n.id] = n.id === nodeId ? 'selected' : 'dimmed'
    }
    for (const e of data.edges) {
      stateMap[e.id] = 'dimmed'
    }

    await g.setElementState(stateMap)
    state.value.selectedNodeId = nodeId
    state.value.dimmed = true
    state.value.highlightedNodeIds = new Set([nodeId])
    g.draw()
  }

  async function clearHighlight() {
    const g = graphInstance()
    const data = graphData()
    if (!g || !data) return

    const stateMap: Record<string, string[]> = {}
    for (const n of data.nodes) stateMap[n.id] = []
    for (const e of data.edges) stateMap[e.id] = []

    await g.setElementState(stateMap)
    state.value.selectedNodeId = null
    state.value.dimmed = false
    state.value.highlightedNodeIds = new Set()
    state.value.pathSourceId = null
    state.value.pathTargetId = null
    g.draw()
  }

  // ── 类型筛选 ──

  function filterByTypes(nodeTypes: string[], edgeTypes: string[]) {
    const g = graphInstance()
    const data = graphData()
    if (!g || !data) return
    const ntSet = new Set(nodeTypes)
    const etSet = new Set(edgeTypes)
    state.value.nodeFilter = ntSet
    state.value.edgeFilter = etSet

    g.updateNodeData(data.nodes.map((n) => ({
      id: n.id,
      style: { visibility: ntSet.has(n.data.nodeType) ? 'visible' : 'hidden' as const },
    })))
    g.updateEdgeData(data.edges.map((e) => ({
      id: e.id,
      style: { visibility: etSet.has(e.data.type) ? 'visible' : 'hidden' as const },
    })))
    g.draw()
  }

  // ── 邻域展开 ──

  async function expandNeighbors(nodeId: string) {
    const g = graphInstance()
    const data = graphData()
    if (!g || !data) return
    const neighborIds = new Set<string>()
    neighborIds.add(nodeId)
    for (const e of data.edges) {
      if (e.source === nodeId) neighborIds.add(e.target)
      if (e.target === nodeId) neighborIds.add(e.source)
    }
    const highlightEdgeIds = new Set(
      data.edges
        .filter((e) => neighborIds.has(e.source) && neighborIds.has(e.target))
        .map((e) => e.id),
    )

    const stateMap: Record<string, string | string[]> = {}
    for (const n of data.nodes) {
      stateMap[n.id] = neighborIds.has(n.id) ? 'highlighted' : 'dimmed'
    }
    for (const e of data.edges) {
      stateMap[e.id] = highlightEdgeIds.has(e.id) ? 'highlighted' : 'dimmed'
    }

    await g.setElementState(stateMap)
    state.value.dimmed = true
    state.value.highlightedNodeIds = neighborIds
    g.draw()
  }

  // ── 路径高亮（BFS 最短路径）──

  async function highlightPath(sourceId: string, targetId: string) {
    const g = graphInstance()
    const data = graphData()
    if (!g || !data) return

    // BFS
    const adj = new Map<string, string[]>()
    for (const e of data.edges) {
      if (!adj.has(e.source)) adj.set(e.source, [])
      if (!adj.has(e.target)) adj.set(e.target, [])
      adj.get(e.source)!.push(e.target)
      adj.get(e.target)!.push(e.source)
    }

    const parent = new Map<string, string>()
    const visited = new Set<string>()
    const queue = [sourceId]
    visited.add(sourceId)

    while (queue.length > 0) {
      const cur = queue.shift()!
      if (cur === targetId) break
      for (const nb of adj.get(cur) ?? []) {
        if (!visited.has(nb)) {
          visited.add(nb)
          parent.set(nb, cur)
          queue.push(nb)
        }
      }
    }

    const pathNodeIds = new Set<string>()
    if (parent.has(targetId) || sourceId === targetId) {
      let cur: string | undefined = targetId
      while (cur) {
        pathNodeIds.add(cur)
        if (cur === sourceId) break
        cur = parent.get(cur)
      }
    }

    const pathEdgeIds = new Set<string>()
    for (const e of data.edges) {
      if (pathNodeIds.has(e.source) && pathNodeIds.has(e.target)) {
        pathEdgeIds.add(e.id)
      }
    }

    const stateMap: Record<string, string | string[]> = {}
    for (const n of data.nodes) {
      stateMap[n.id] = pathNodeIds.has(n.id) ? 'pathHighlighted' : 'dimmed'
    }
    for (const e of data.edges) {
      stateMap[e.id] = pathEdgeIds.has(e.id) ? 'pathHighlighted' : 'dimmed'
    }

    await g.setElementState(stateMap)
    state.value.pathSourceId = sourceId
    state.value.pathTargetId = targetId
    state.value.dimmed = true
    g.draw()
  }

  async function focusNode(nodeId: string) {
    const g = graphInstance()
    if (!g) return
    try {
      await g.focusElement(nodeId, { duration: 300, easing: 'ease-out' })
    } catch {
      // focusElement 静默降级
    }
  }

  return {
    state,
    allNodeTypes,
    allEdgeTypes,
    stats,
    search,
    highlightNode,
    clearHighlight,
    filterByTypes,
    expandNeighbors,
    highlightPath,
    focusNode,
  }
}