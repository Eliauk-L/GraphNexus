<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import cytoscape from 'cytoscape'
import type { CyElements } from '../graphAdapter'

const props = defineProps<{
  data: CyElements | null
}>()

const container = ref<HTMLDivElement>()
let cy: cytoscape.Core | null = null

function createCy() {
  if (!container.value || !props.data) return
  if (cy) { cy.destroy(); cy = null }

  const elements: cytoscape.ElementDefinition[] = [
    ...props.data.nodes.map((n) => ({ group: 'nodes' as const, data: n.data })),
    ...props.data.edges.map((e) => ({ group: 'edges' as const, data: e.data })),
  ]

  cy = cytoscape({
    container: container.value,
    elements,
    style: [
      {
        selector: 'node',
        style: {
          'background-color': 'data(color)',
          'width': 'data(size)',
          'height': 'data(size)',
          'label': 'data(label)',
          'font-size': '11px',
          'color': '#333',
          'text-valign': 'bottom',
          'text-halign': 'center',
          'text-margin-y': 6,
          'border-width': 2,
          'border-color': '#fff',
        },
      },
      {
        selector: 'edge',
        style: {
          'width': 'data(width)',
          'line-color': 'data(color)',
          'target-arrow-color': 'data(color)',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier',
          'line-style': (el: any) => el.data('lineStyle') ?? 'solid',
          'arrow-scale': 0.8,
        },
      },
      {
        selector: 'edge[label]',
        style: {
          'label': 'data(type)',
          'font-size': '9px',
          'color': '#666',
          'text-background-color': '#fff',
          'text-background-opacity': 0.8,
          'text-background-padding': '2px',
        },
      },
      {
        selector: ':selected',
        style: {
          'border-color': '#4A90D9',
          'border-width': 3,
        },
      },
    ],
    layout: {
      name: 'cose',
      animate: false,
      nodeRepulsion: () => 8000,
      idealEdgeLength: () => 120,
      gravity: 0.25,
      numIter: 1000,
    },
    wheelSensitivity: 0.3,
  })
}

watch(() => props.data, () => {
  createCy()
}, { deep: true })

onMounted(() => {
  nextTick(() => createCy())
})

onBeforeUnmount(() => {
  if (cy) {
    cy.destroy()
    cy = null
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