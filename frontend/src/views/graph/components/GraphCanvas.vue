<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Network } from 'vis-network'
import { DataSet } from 'vis-data'
import type { VisGraphData } from '../graphAdapter'

const props = defineProps<{
  data: VisGraphData | null
}>()

const container = ref<HTMLDivElement>()
let network: Network | null = null

function createNetwork() {
  if (!container.value || !props.data) return
  if (network) { network.destroy(); network = null }

  network = new Network(
    container.value,
    {
      nodes: new DataSet(props.data.nodes),
      edges: new DataSet(props.data.edges),
    },
    {
      physics: {
        solver: 'forceAtlas2Based',
        forceAtlas2Based: {
          gravitationalConstant: -50,
          centralGravity: 0.01,
          springLength: 150,
          springConstant: 0.08,
        },
        stabilization: { iterations: 100 },
      },
      edges: {
        smooth: { enabled: true, type: 'continuous', roundness: 0.5 },
        font: { size: 9, color: '#666', strokeWidth: 2, strokeColor: '#fff' },
      },
      interaction: {
        hover: true,
        tooltipDelay: 200,
        zoomView: true,
        dragView: true,
      },
    },
  )
}

watch(() => props.data, () => {
  createNetwork()
}, { deep: true })

onMounted(() => {
  nextTick(() => createNetwork())
})

onBeforeUnmount(() => {
  if (network) {
    network.destroy()
    network = null
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