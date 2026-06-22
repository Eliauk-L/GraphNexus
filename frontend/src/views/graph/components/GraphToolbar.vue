<script setup lang="ts">
/**
 * GraphToolbar — 图谱搜索框 + 视图模式切换。
 * 见 UI-DESIGN §4.1。
 */
import { ref, watch, computed } from 'vue'
import BaseInput from '@/common/components/BaseInput.vue'

export type ViewMode = 'document' | 'full'

const props = defineProps<{
  viewMode: ViewMode
  searchResults?: { id: string; label: string; nodeType: string }[]
}>()

const emit = defineEmits<{
  'update:viewMode': [mode: ViewMode]
  'search': [query: string]
  'select-node': [nodeId: string]
}>()

const modes: { value: ViewMode; label: string }[] = [
  { value: 'document', label: '文档子图' },
  { value: 'full', label: '全量图谱' },
]

const searchQuery = ref('')
const showDropdown = computed(() => (props.searchResults?.length ?? 0) > 0 && searchQuery.value.length >= 2)

let debounceTimer: ReturnType<typeof setTimeout> | null = null

function onSearchInput(value: string) {
  searchQuery.value = value
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    emit('search', value)
  }, 300)
}

function onSelectNode(nodeId: string) {
  emit('select-node', nodeId)
  searchQuery.value = ''
}

function onSelectMode(mode: ViewMode) {
  emit('update:viewMode', mode)
}

// 搜索结果节点类型颜色
function nodeTypeColor(type: string): string {
  const colors: Record<string, string> = {
    KnowledgePoint: '#3B82F6',
    Student: '#EC4899',
    Exam: '#8B5CF6',
    KnowledgeCategory: '#EAB308',
    Document: '#10B981',
    Entity: '#F97316',
  }
  return colors[type] ?? '#9CA3AF'
}
</script>

<template>
  <div class="graph-toolbar">
    <div class="search-area">
      <div class="search-wrapper">
        <BaseInput
          :model-value="searchQuery"
          placeholder="搜索节点..."
          style="width: 280px"
          @update:model-value="onSearchInput"
        />
        <!-- 搜索下拉 -->
        <div v-if="showDropdown" class="search-dropdown">
          <div
            v-for="item in searchResults"
            :key="item.id"
            class="search-item"
            @click="onSelectNode(item.id)"
          >
            <span
              class="search-item-dot"
              :style="{ background: nodeTypeColor(item.nodeType) }"
            />
            <span class="search-item-label">{{ item.label }}</span>
            <span class="search-item-type supporting">{{ item.nodeType }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 视图模式 Segmented Toggle -->
    <div class="segmented-toggle">
      <button
        v-for="mode in modes"
        :key="mode.value"
        class="seg-btn"
        :class="{ active: props.viewMode === mode.value }"
        @click="onSelectMode(mode.value)"
      >
        {{ mode.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  min-height: 36px;
}

.search-area {
  position: relative;
}

.search-wrapper {
  position: relative;
}

.search-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-sm);
  max-height: 200px;
  overflow-y: auto;
  z-index: 30;
  box-shadow: var(--shadow-hover-lift);
}

.search-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  height: 32px;
  cursor: pointer;
  font-size: 0.875rem;
  color: var(--color-text-primary);
}
.search-item:hover {
  background: var(--color-brand-veil);
}

.search-item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.search-item-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-item-type {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.segmented-toggle {
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-sm);
  overflow: hidden;
}

.seg-btn {
  padding: 6px 14px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
  white-space: nowrap;
}
.seg-btn:not(:last-child) {
  border-right: 1px solid var(--color-border);
}
.seg-btn:hover {
  background: var(--color-brand-veil);
}
.seg-btn.active {
  background: var(--color-brand);
  color: var(--color-text-on-brand);
}
</style>