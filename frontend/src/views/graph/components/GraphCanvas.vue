<script setup lang="ts">
/**
 * GraphCanvas — G6 v5 图谱渲染组件。
 * 管理 G6 Graph 实例生命周期：创建 → render（异步）→ setData+draw（增量）→ destroy。
 */
import { ref, watch, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { Graph } from '@antv/g6'
import type { G6GraphData } from '../graphAdapter'
import {
  DEFAULT_NODE_SIZE,
  DEFAULT_NODE_COLOR,
  DEFAULT_EDGE_COLOR,
  DEFAULT_EDGE_WIDTH,
  DEFAULT_EDGE_OPACITY,
} from '../constants'

const props = defineProps<{
  data: G6GraphData | null
  loading?: boolean
  error?: string | null
  fullscreen?: boolean
}>()

const emit = defineEmits<{
  'node-click': [nodeId: string, nodeData: Record<string, unknown>]
  'node-ctrl-click': [nodeId: string]
  'ready': [graph: Graph]
}>()

const container = ref<HTMLDivElement>()
const wrapperStyle = computed(() => ({
  flex: '1',
  position: 'relative' as const,
  minHeight: '300px',
  width: '100%',
}))

const canvasStyle = computed(() => ({
  position: 'absolute' as const,
  inset: '0',
}))
let graph: Graph | null = null
let lastNodeCount = 0
const LOADING_STYLE_ID = 'g6-loading-overlay'

function showLoading() {
  if (!container.value) return
  let el = document.getElementById(LOADING_STYLE_ID)
  if (!el) {
    el = document.createElement('div')
    el.id = LOADING_STYLE_ID
    el.style.cssText =
      'position:absolute;inset:0;display:flex;align-items:center;justify-content:center;' +
      'background:var(--color-brand-veil,oklch(0.55 0.18 250 / 0.12));z-index:10;border-radius:8px'
    el.textContent = '加载图谱中…'
    el.style.color = 'var(--color-text-tertiary)'
    el.style.fontSize = '0.875rem'
    container.value.style.position = 'relative'
    container.value.appendChild(el)
  }
}

function hideLoading() {
  const el = document.getElementById(LOADING_STYLE_ID)
  if (el) el.remove()
}

async function createGraph() {
  if (!container.value) {
    console.warn('[GraphCanvas] createGraph skipped: no container')
    return
  }
  if (!props.data || props.data.nodes.length === 0) {
    console.debug('[GraphCanvas] createGraph skipped: no data')
    return
  }

  // 销毁旧实例
  if (graph) {
    hideLoading()
    graph.destroy()
    graph = null
  }

  // 确保容器有尺寸（G6 需要）
  const rect = container.value.getBoundingClientRect()
  console.debug('[GraphCanvas] container size:', rect.width, 'x', rect.height)
  if (rect.width === 0 || rect.height === 0) {
    console.warn('[GraphCanvas] container has zero size, delaying...')
    // 延迟重试
    setTimeout(() => {
      if (props.data && props.data.nodes.length > 0) createGraph()
    }, 100)
    return
  }

  const elements = {
    nodes: props.data.nodes.map((n) => ({
      id: n.id,
      data: { ...n.data },
    })),
    edges: props.data.edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      data: { ...e.data },
    })),
  }
  console.debug('[GraphCanvas] creating graph:', elements.nodes.length, 'nodes,', elements.edges.length, 'edges')

  try {
    graph = new Graph({
      container: container.value,
      data: elements,
      autoFit: 'view' as const,
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
          endArrow: true,
          lineDash: (d: any) => {
            const ls = d.data?.lineStyle
            if (ls === 'dashed') return [8, 4]
            if (ls === 'dotted') return [2, 4]
            return undefined
          },
          labelText: (d: any) => d.data?.type ?? '',
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
        animate: false,
        nodeStrength: -8000,
        linkDistance: 120,
        gravity: 0.25,
        maxIterations: 1000,
      },
      behaviors: [
        'drag-canvas',
        'zoom-canvas',
        { type: 'hover-activate', degree: 1, state: 'hover' },
      ],
    })

    // G6 v5 构造函数传入 data 后会异步布局+渲染，等待完成
    await graph.render()
    console.debug('[GraphCanvas] graph rendered successfully')
  } catch (err) {
    console.error('[GraphCanvas] graph creation failed:', err)
    graph = null
    return
  }

  graph.on('node:click', (evt: any) => {
    const nodeId = evt.target?.id
    if (!nodeId) return
    if (evt.originalEvent?.ctrlKey || evt.originalEvent?.metaKey) {
      emit('node-ctrl-click', nodeId)
    } else {
      const nodeData = props.data?.nodes.find((n) => n.id === nodeId)?.data
      emit('node-click', nodeId, nodeData ?? {})
    }
  })

  lastNodeCount = props.data.nodes.length
  emit('ready', graph)
  hideLoading()
}

async function updateData() {
  if (!graph || !props.data) return
  const newNodeCount = props.data.nodes.length

  // 防御：节点数变化 > 5× 时走重建路径
  if (lastNodeCount > 0 && (newNodeCount / lastNodeCount > 5 || lastNodeCount / newNodeCount > 5)) {
    console.debug('[GraphCanvas] large size change, recreating graph')
    graph.destroy()
    graph = null
    lastNodeCount = 0
    await createGraph()
    return
  }

  const elements = {
    nodes: props.data.nodes.map((n) => ({ id: n.id, data: { ...n.data } })),
    edges: props.data.edges.map((e) => ({ id: e.id, source: e.source, target: e.target, data: { ...e.data } })),
  }

  try {
    graph.setData(elements)
    await graph.render()
  } catch (err) {
    console.error('[GraphCanvas] updateData failed:', err)
  }
  lastNodeCount = newNodeCount
}

watch(
  () => props.data,
  async (newData, oldData) => {
    console.debug('[GraphCanvas] data watch fired, nodes:', newData?.nodes?.length, 'old:', !!oldData)
    if (!newData || newData.nodes.length === 0) return
    await nextTick()
    console.debug('[GraphCanvas] container ref:', !!container.value, 'graph exists:', !!graph)
    if (!graph) {
      await createGraph()
    } else {
      await updateData()
    }
  },
)

watch(
  () => props.loading,
  (v) => {
    if (v) showLoading()
  },
)

watch(
  () => props.fullscreen,
  async () => {
    await nextTick()
    setTimeout(async () => {
      if (!graph || !container.value) return
      try {
        const rect = container.value.getBoundingClientRect()
        console.debug('[GraphCanvas] fullscreen resize:', rect.width, 'x', rect.height)
        await graph.setSize(rect.width, rect.height)
        await graph.render()
      } catch (e) {
        console.warn('[GraphCanvas] fullscreen resize failed, recreating:', e)
        graph?.destroy()
        graph = null
        if (props.data && props.data.nodes.length > 0) {
          await createGraph()
        }
      }
    }, 400)
  },
)

onMounted(() => {
  nextTick(async () => {
    if (props.data && props.data.nodes.length > 0) {
      await createGraph()
    }
  })
})

onBeforeUnmount(() => {
  hideLoading()
  if (graph) {
    graph.destroy()
    graph = null
  }
})

defineExpose({ getGraph: () => graph })
</script>

<template>
  <div class="graph-canvas-wrapper" :style="wrapperStyle">
    <!-- 空态 -->
    <div v-if="!props.data && !props.loading" class="graph-empty">
      <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--color-text-tertiary)" stroke-width="1.5" stroke-linecap="round">
        <circle cx="12" cy="12" r="3" />
        <path d="M12 2v4m0 12v4M2 12h4m12 0h4" />
      </svg>
      <p class="supporting" style="color: var(--color-text-tertiary); margin-top: 12px">
        选择文档以查看知识图谱子图
      </p>
    </div>

    <!-- 错误态 -->
    <div v-if="props.error" class="graph-error">
      <p class="supporting" style="color: var(--color-error)">{{ props.error }}</p>
    </div>

    <!-- G6 画布：始终渲染，空态用绝对定位覆盖 -->
    <div ref="container" class="graph-canvas" :style="canvasStyle" />
  </div>
</template>

<style scoped>
.graph-canvas-wrapper {
  width: 100%;
}

.graph-canvas {
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  overflow: hidden;
  background: var(--color-surface);
}

.graph-empty,
.graph-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  background: var(--color-surface);
}
</style>