<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { queryPageRank, queryDegree } from '@/api/graph'
import type { MetricResultVO } from '@/api/types'
import { NTag } from 'naive-ui'

interface MetricRow { name: string; value: number; nodeType: string }
const kpPageRank = ref<MetricRow[]>([])
const stPageRank = ref<MetricRow[]>([])
const kpDegree = ref<MetricRow[]>([])
const stDegree = ref<MetricRow[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const NODE_TYPE_LABELS: Record<string, string> = { KnowledgePoint: '知识点', Student: '学生' }

async function loadMetrics() {
  loading.value = true
  error.value = null
  try {
    const [prKp, prSt, degKp, degSt] = await Promise.all([
      queryPageRank(['KnowledgePoint']),
      queryPageRank(['Student', 'KnowledgePoint']),
      queryDegree(['KnowledgePoint']),
      queryDegree(['Student', 'KnowledgePoint']),
    ])
    kpPageRank.value = top5(prKp, 'KnowledgePoint')
    stPageRank.value = top5(prSt, 'Student')
    kpDegree.value = top5(degKp, 'KnowledgePoint')
    stDegree.value = top5(degSt, 'Student')
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

function top5(list: MetricResultVO[], nodeType: string): MetricRow[] {
  return (Array.isArray(list) ? list : [])
    .filter(m => m.nodeType === nodeType)
    .sort((a, b) => b.metricValue - a.metricValue)
    .slice(0, 5)
    .map(m => ({ name: m.nodeName ?? m.nodeId, value: m.metricValue, nodeType: m.nodeType }))
}

function fmt(v: number) { return v.toFixed(4) }

onMounted(loadMetrics)
</script>

<template>
  <section class="panel">
    <h2 class="headline">图度量指标 <NTag size="tiny" :bordered="true" style="vertical-align:middle;margin-left:8px">Top 5</NTag></h2>

    <div v-if="error" class="supporting" style="color:var(--color-error)">{{ error }}</div>

    <div class="metrics-grid">
      <!-- PageRank · 知识点 -->
      <div class="metric-col">
        <div class="supporting" style="color:var(--color-text-tertiary);margin-bottom:4px">PageRank · 知识点</div>
        <table class="metric-table" v-if="kpPageRank.length">
          <tr v-for="(r, i) in kpPageRank" :key="r.name">
            <td class="rank">{{ i + 1 }}</td>
            <td class="name">{{ r.name }}</td>
            <td class="mono val">{{ fmt(r.value) }}</td>
          </tr>
        </table>
        <div v-else-if="!loading" class="supporting" style="color:var(--color-text-tertiary)">—</div>
      </div>

      <!-- PageRank · 学生 -->
      <div class="metric-col">
        <div class="supporting" style="color:var(--color-text-tertiary);margin-bottom:4px">PageRank · 学生</div>
        <table class="metric-table" v-if="stPageRank.length">
          <tr v-for="(r, i) in stPageRank" :key="r.name">
            <td class="rank">{{ i + 1 }}</td>
            <td class="name">{{ r.name }}</td>
            <td class="mono val">{{ fmt(r.value) }}</td>
          </tr>
        </table>
        <div v-else-if="!loading" class="supporting" style="color:var(--color-text-tertiary)">—</div>
      </div>

      <!-- 度中心性 · 知识点 -->
      <div class="metric-col">
        <div class="supporting" style="color:var(--color-text-tertiary);margin-bottom:4px">度中心性 · 知识点</div>
        <table class="metric-table" v-if="kpDegree.length">
          <tr v-for="(r, i) in kpDegree" :key="r.name">
            <td class="rank">{{ i + 1 }}</td>
            <td class="name">{{ r.name }}</td>
            <td class="mono val">{{ r.value }}</td>
          </tr>
        </table>
        <div v-else-if="!loading" class="supporting" style="color:var(--color-text-tertiary)">—</div>
      </div>

      <!-- 度中心性 · 学生 -->
      <div class="metric-col">
        <div class="supporting" style="color:var(--color-text-tertiary);margin-bottom:4px">度中心性 · 学生</div>
        <table class="metric-table" v-if="stDegree.length">
          <tr v-for="(r, i) in stDegree" :key="r.name">
            <td class="rank">{{ i + 1 }}</td>
            <td class="name">{{ r.name }}</td>
            <td class="mono val">{{ r.value }}</td>
          </tr>
        </table>
        <div v-else-if="!loading" class="supporting" style="color:var(--color-text-tertiary)">—</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.panel { display: flex; flex-direction: column; gap: var(--spacing-md); }

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-sm);
}

.metric-col {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-md);
  padding: var(--spacing-md);
}

.metric-table { width: 100%; border-collapse: collapse; }
.metric-table td { padding: 4px 0; font-size: 0.8125rem; border-bottom: 1px solid var(--color-border); }
.metric-table tr:last-child td { border-bottom: none; }
.rank { width: 20px; color: var(--color-text-tertiary); font-weight: 500; }
.name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 0; }
.val { text-align: right; white-space: nowrap; color: var(--color-text-primary); }
</style>