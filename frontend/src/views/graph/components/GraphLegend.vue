<script setup lang="ts">
/**
 * GraphLegend — 类型筛选面板（checkbox 组）。
 * 见 UI-DESIGN §4.2。
 */
import { ref, watch } from 'vue'
import {
  NODE_COLORS,
  EDGE_COLORS,
  EDGE_LINE_STYLES,
  DEFAULT_NODE_COLOR,
  DEFAULT_EDGE_COLOR,
} from '../constants'

const props = defineProps<{
  nodeTypes: string[]
  edgeTypes: string[]
}>()

const emit = defineEmits<{
  'update:nodeFilter': [types: string[]]
  'update:edgeFilter': [types: string[]]
}>()

const activeNodeTypes = ref<string[]>([...props.nodeTypes])
const activeEdgeTypes = ref<string[]>([...props.edgeTypes])

watch(
  () => [props.nodeTypes, props.edgeTypes],
  () => {
    activeNodeTypes.value = [...props.nodeTypes]
    activeEdgeTypes.value = [...props.edgeTypes]
  },
)

function toggleNodeType(type: string) {
  if (activeNodeTypes.value.includes(type)) {
    activeNodeTypes.value = activeNodeTypes.value.filter((t) => t !== type)
  } else {
    activeNodeTypes.value = [...activeNodeTypes.value, type]
  }
  emit('update:nodeFilter', [...activeNodeTypes.value])
}

function toggleEdgeType(type: string) {
  if (activeEdgeTypes.value.includes(type)) {
    activeEdgeTypes.value = activeEdgeTypes.value.filter((t) => t !== type)
  } else {
    activeEdgeTypes.value = [...activeEdgeTypes.value, type]
  }
  emit('update:edgeFilter', [...activeEdgeTypes.value])
}

function nodeColor(type: string) {
  return NODE_COLORS[type] ?? DEFAULT_NODE_COLOR
}

function edgeColor(type: string) {
  return EDGE_COLORS[type] ?? DEFAULT_EDGE_COLOR
}

function edgeDash(type: string) {
  return EDGE_LINE_STYLES[type] === 'dashed'
}
</script>

<template>
  <div v-if="nodeTypes.length > 0 || edgeTypes.length > 0" class="graph-legend">
    <!-- 节点类型 -->
    <div v-if="nodeTypes.length > 0" class="legend-group">
      <span class="legend-label">节点类型</span>
      <label
        v-for="type in nodeTypes"
        :key="'n-' + type"
        class="legend-item"
      >
        <input
          type="checkbox"
          :checked="activeNodeTypes.includes(type)"
          @change="toggleNodeType(type)"
        />
        <span class="legend-swatch" :style="{ background: nodeColor(type) }" />
        <span class="legend-name">{{ type }}</span>
      </label>
    </div>

    <!-- 边类型 -->
    <div v-if="edgeTypes.length > 0" class="legend-group">
      <span class="legend-label">边类型</span>
      <label
        v-for="type in edgeTypes"
        :key="'e-' + type"
        class="legend-item"
      >
        <input
          type="checkbox"
          :checked="activeEdgeTypes.includes(type)"
          @change="toggleEdgeType(type)"
        />
        <span
          class="legend-edge-swatch"
          :style="{
            background: edgeColor(type),
            borderStyle: edgeDash(type) ? 'dashed' : 'solid',
          }"
        />
        <span class="legend-name">{{ type }}</span>
      </label>
    </div>
  </div>
</template>

<style scoped>
.graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-lg);
  padding: var(--spacing-sm) 0;
  border-top: 1px solid var(--color-border);
}

.legend-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.legend-label {
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
  margin-right: 4px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  color: var(--color-text-primary);
  user-select: none;
}
.legend-item input {
  display: none;
}
.legend-item input:not(:checked) ~ .legend-swatch,
.legend-item input:not(:checked) ~ .legend-edge-swatch {
  opacity: 0.3;
}
.legend-item input:not(:checked) ~ .legend-name {
  color: var(--color-text-tertiary);
}

.legend-swatch {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-edge-swatch {
  width: 16px;
  height: 2px;
  flex-shrink: 0;
  border-top-width: 2px;
}

.legend-name {
  white-space: nowrap;
}
</style>