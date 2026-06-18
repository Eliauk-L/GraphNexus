<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useGraphStore } from './graphStore'
import { transformGraphSubgraphVO } from './graphAdapter'
import type { G6GraphData } from './graphAdapter'
import BaseSelect from '@/common/components/BaseSelect.vue'
import BaseCard from '@/common/components/BaseCard.vue'
import GraphCanvas from './components/GraphCanvas.vue'

const store = useGraphStore()
const route = useRoute()
const selectedDocId = ref<number | null>(null)
const g6Data = ref<G6GraphData | null>(null)

const docOptions = computed(() =>
  store.documents.map((d) => ({ label: `${d.name} (ID: ${d.documentId})`, value: d.documentId }))
)

async function handleSelect(docId: number) {
  selectedDocId.value = docId
  await store.loadSubgraph(docId)
  if (store.currentGraph) {
    g6Data.value = transformGraphSubgraphVO(store.currentGraph)
  }
}

onMounted(async () => {
  await store.loadDocuments()
  // 路由中有 document/:id → 直接加载
  const docId = route.params.id
  if (docId) {
    handleSelect(Number(docId))
  }
})
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">图谱可视化</h1>
    </div>

    <BaseCard>
      <template #header>
        <div class="graph-controls">
          <BaseSelect
            :model-value="selectedDocId"
            :options="docOptions"
            placeholder="选择一个已抽取的文档"
            style="width: 320px"
            @update:model-value="(v) => handleSelect(v as number)"
          />
        </div>
      </template>

      <GraphCanvas v-if="g6Data" :data="g6Data" />
      <div v-else class="graph-empty body" style="color: var(--color-text-tertiary)">
        选择文档以查看知识图谱子图
      </div>
    </BaseCard>
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: var(--spacing-lg);
}

.graph-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.graph-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
}
</style>