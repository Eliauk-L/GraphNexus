<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { NVL } from '@neo4j-nvl/base'
import type { NvlGraphData } from '../graphAdapter'

const props = defineProps<{
  data: NvlGraphData | null
}>()

const container = ref<HTMLDivElement>()
let nvl: NVL | null = null

function createNvl() {
  if (!container.value || !props.data) return
  if (nvl) { nvl.destroy(); nvl = null }

  nvl = new NVL(
    container.value,
    props.data.nodes,
    props.data.relationships,
    {
      renderer: 'canvas',
      layout: 'forceDirected',
      layoutOptions: {
        enableCytoscape: false,
      },
      initialZoom: 0.8,
    },
  )
}

watch(() => props.data, () => {
  createNvl()
}, { deep: true })

onMounted(() => {
  nextTick(() => createNvl())
})

onBeforeUnmount(() => {
  if (nvl) {
    nvl.destroy()
    nvl = null
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