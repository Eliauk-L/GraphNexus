<script setup lang="ts">
/**
 * GraphToolbar — 图谱搜索框。
 */
import { ref, computed } from 'vue'
import BaseInput from '@/common/components/BaseInput.vue'

const props = defineProps<{
  searchResults?: { id: string; label: string; nodeType: string }[]
}>()

const emit = defineEmits<{
  'search': [query: string]
  'select-node': [nodeId: string]
}>()

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
  </div>
</template>

<style scoped>
.graph-toolbar {
  display: flex;
  align-items: center;
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
</style>