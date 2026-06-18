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
  if (!container.value) return
  if (graph) { graph.destroy(); graph = null }

  graph = new Graph({
    container: container.value,
    width: container.value.clientWidth,
    height: 500,
    data: (props.data ?? { nodes: [], edges: [] }) as any,
    layout: {
      type: 'force',
      preventOverlap: true,
      nodeStrength: -200,
      edgeStrength: 0.2,
    },
    node: {
      style: {
        labelText: (d: any) => d.label,
        labelFontSize: 12,
        labelFontFamily: 'PingFang SC, Microsoft YaHei, sans-serif',
        labelFill: 'oklch(0.15 0.005 95)',
        labelPlacement: 'bottom',
        labelOffsetY: 6,
      },
    },
    edge: {
      style: {
        endArrow: true,
        labelText: (d: any) => d.type,
        labelFontSize: 10,
        labelFill: 'oklch(0.45 0.005 95)',
        labelBackground: true,
        labelBackgroundFill: 'oklch(1 0 0)',
        labelBackgroundOpacity: 0.8,
      },
    },
    behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element', {
      type: 'hover-activate',
      degree: 1,
      direction: 'both',
    }],
  })

  graph.render()
}

watch(() => props.data, (newData) => {
  if (!graph || !newData) return
  graph.setData(newData as any)
  graph.render()
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
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  overflow: hidden;
  background: var(--color-surface);
}
</style>