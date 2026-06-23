<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { NSelect, NSpin } from 'naive-ui'
import { getSubjectGraph, listSubjects } from '@/api/graph'
import { toGraphData } from '@/views/graph/graphAdapter'
import type { GraphSubgraphVO } from '@/api/types'
import { Graph } from '@antv/g6'

const props = defineProps<{
  subject: string
}>()

const emit = defineEmits<{
  'update:subject': [value: string]
}>()

interface SubjectOption { label: string; value: string }
const subjects = ref<SubjectOption[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const containerRef = ref<HTMLDivElement>()
let graph: Graph | null = null

const subjectOptions = [{ label: '全部学科', value: '' }, ...subjects.value]

// 加载学科列表
async function loadSubjects() {
  try {
    const data = await listSubjects()
    subjects.value = data.map((s: string) => ({ label: s, value: s }))
  } catch { /* ignore */ }
}

// 加载并渲染图谱
async function loadGraph(subjectName: string) {
  if (!containerRef.value) return
  loading.value = true
  error.value = null

  try {
    const data: GraphSubgraphVO = subjectName
      ? await getSubjectGraph(subjectName)
      : await getSubjectGraph('') // 全部学科走默认

    const g6Data = toGraphData(data)
    if (!g6Data || !g6Data.nodes.length) {
      error.value = subjectName ? `学科"${subjectName}"暂无图谱数据` : '暂无图谱数据'
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

onMounted(() => {
  loadSubjects()
  if (props.subject) loadGraph(props.subject)
})

watch(() => props.subject, (val) => loadGraph(val))

onBeforeUnmount(() => {
  if (graph) { graph.destroy(); graph = null }
})
</script>

<template>
  <div class="graph-overview">
    <div class="graph-overview__header">
      <h3 class="title" style="margin:0">全量图谱可视化</h3>
      <NSelect
        :value="props.subject"
        :options="[{ label: '全部学科', value: '' }, ...subjects]"
        size="small"
        style="width: 160px"
        placeholder="选择学科"
        @update:value="(v: string) => emit('update:subject', v)"
      />
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
  height: 480px;
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