<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Graph } from '@antv/g6'
import type { G6GraphData } from '../graphAdapter'

const props = defineProps<{
  data: G6GraphData | null
}>()

const container = ref<HTMLDivElement>()
let graph: Graph | null = null

function initGraph() {
  if (!container.value || !props.data) return
  if (graph) { graph.destroy(); graph = null }

  graph = new Graph({
    container: container.value,
    width: container.value.clientWidth,
    height: 500,
    data: (props.data ?? { nodes: [], edges: [] }) as any,
    layout: {
      type: 'force',
      preventOverlap: true,
      animated: true,
    },
    node: (d: any) => ({
      style: {
        fill: d.data?.fill ?? '#1783FF',
        size: d.data?.size ?? 28,
        labelText: d.data?.label ?? d.id,
        labelFontSize: 12,
        labelPlacement: 'bottom',
        labelOffsetY: 6,
        labelFill: '#333',
      },
    }),
    edge: (d: any) => ({
      style: {
        stroke: d.data?.stroke ?? '#99ADD1',
        lineWidth: d.data?.lineWidth ?? 1.5,
        endArrow: true,
      },
    }),
    behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element'],
  } as any)

  graph.render()
}

watch(() => props.data, () => {
  initGraph()
}, { deep: true })

onMounted(() => {
  nextTick(() => initGraph())
})

onBeforeUnmount(() => {
  if (graph) {
    graph.destroy()
    graph = null
  }
})
</script>

<template>
  <div ref="container" class="graph-canvas" />
</template>

<style scoped>
.graph-canvas {
  width: 100%;
  height: 500px;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  overflow: hidden;
  background: var(--color-surface);
}
</style>