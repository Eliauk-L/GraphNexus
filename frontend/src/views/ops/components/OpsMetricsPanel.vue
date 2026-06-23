<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { queryPageRank, queryDegree } from '@/api/graph'
import type { MetricResultVO } from '@/api/types'
import { NTag } from 'naive-ui'

interface MetricRow { name: string; value: number; nodeType: string; subject: string; className: string }
const kpPR = ref<MetricRow[]>([])
const stPR = ref<MetricRow[]>([])
const kpDeg = ref<MetricRow[]>([])
const stDeg = ref<MetricRow[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

async function load() {
  loading.value = true; error.value = null
  try {
    const [a, b, c, d] = await Promise.all([
      queryPageRank(['KnowledgePoint']),
      queryPageRank(['Student', 'KnowledgePoint']),
      queryDegree(['KnowledgePoint']),
      queryDegree(['Student', 'KnowledgePoint']),
    ])
    console.log('[OpsMetrics] pagerank(KP):', a?.length, 'items')
    console.log('[OpsMetrics]   types:', [...new Set(a?.map(m => m.nodeType) ?? [])])
    console.log('[OpsMetrics]   KP count:', a?.filter(m => m.nodeType === 'KnowledgePoint').length ?? 0)
    console.log('[OpsMetrics] pagerank(St+KP):', b?.length, 'items, types:', [...new Set(b?.map(m => m.nodeType) ?? [])])
    console.log('[OpsMetrics] degree(KP):', c?.length, 'items, types:', [...new Set(c?.map(m => m.nodeType) ?? [])])
    console.log('[OpsMetrics] degree(St+KP):', d?.length, 'items, types:', [...new Set(d?.map(m => m.nodeType) ?? [])])
    kpPR.value = top(a, 'KnowledgePoint'); stPR.value = top(b, 'Student')
    kpDeg.value = top(c, 'KnowledgePoint'); stDeg.value = top(d, 'Student')
    console.log('[OpsMetrics] top results: kpPR=%d stPR=%d kpDeg=%d stDeg=%d',
      kpPR.value.length, stPR.value.length, kpDeg.value.length, stDeg.value.length)
  } catch (e: any) { error.value = e?.message ?? '加载失败' }
  finally { loading.value = false }
}

function top(list: MetricResultVO[], t: string): MetricRow[] {
  return list.filter(m => m.nodeType === t).sort((a, b) => b.metricValue - a.metricValue).slice(0, 5)
    .map(m => ({ name: m.nodeName ?? m.nodeId, value: m.metricValue, nodeType: m.nodeType, subject: m.subject ?? '', className: m.className ?? '' }))
}

function fmt(v: number) { return v.toFixed(4) }
function sub(r: MetricRow) { return r.nodeType === 'KnowledgePoint' ? r.subject : '' }
function cls(r: MetricRow) { return r.nodeType === 'Student' ? r.className : '' }

onMounted(load)
</script>

<template>
  <section class="panel">
    <h2 class="headline">图度量指标 <NTag size="tiny" :bordered="true" style="vertical-align:middle;margin-left:8px">Top 5</NTag></h2>
    <div v-if="error" class="supporting" style="color:var(--color-error)">{{ error }}</div>

    <div class="grid">
      <div class="col">
        <div class="col-title">PageRank · 知识点</div>
        <table v-if="kpPR.length"><tr v-for="(r,i) in kpPR" :key="i"><td class="rk">{{ i+1 }}</td><td class="nm">{{ r.name }}<span class="sub">{{ sub(r) }}</span></td><td class="vl mono">{{ fmt(r.value) }}</td></tr></table>
        <div v-else class="empty">—</div>
      </div>
      <div class="col">
        <div class="col-title">PageRank · 学生</div>
        <table v-if="stPR.length"><tr v-for="(r,i) in stPR" :key="i"><td class="rk">{{ i+1 }}</td><td class="nm">{{ r.name }}<span class="sub">{{ cls(r) }}</span></td><td class="vl mono">{{ fmt(r.value) }}</td></tr></table>
        <div v-else class="empty">—</div>
      </div>
      <div class="col">
        <div class="col-title">度中心性 · 知识点</div>
        <table v-if="kpDeg.length"><tr v-for="(r,i) in kpDeg" :key="i"><td class="rk">{{ i+1 }}</td><td class="nm">{{ r.name }}<span class="sub">{{ sub(r) }}</span></td><td class="vl mono">{{ r.value }}</td></tr></table>
        <div v-else class="empty">—</div>
      </div>
      <div class="col">
        <div class="col-title">度中心性 · 学生</div>
        <table v-if="stDeg.length"><tr v-for="(r,i) in stDeg" :key="i"><td class="rk">{{ i+1 }}</td><td class="nm">{{ r.name }}<span class="sub">{{ cls(r) }}</span></td><td class="vl mono">{{ r.value }}</td></tr></table>
        <div v-else class="empty">—</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.panel { display:flex;flex-direction:column;gap:var(--spacing-md); }
.grid { display:grid;grid-template-columns:repeat(4,1fr);gap:var(--spacing-sm); }
.col { background:var(--color-surface);border:1px solid var(--color-border);border-radius:var(--rounded-md);padding:var(--spacing-md); }
.col-title { font-size:0.75rem;color:var(--color-text-tertiary);margin-bottom:6px; }
table { width:100%;border-collapse:collapse; }
td { padding:3px 0;font-size:0.8125rem;border-bottom:1px solid var(--color-border); }
tr:last-child td { border-bottom:none; }
.rk { width:18px;color:var(--color-text-tertiary);font-weight:500; }
.nm { overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:0; }
.sub { color:var(--color-text-tertiary);font-size:0.6875rem;margin-left:4px; }
.vl { text-align:right;white-space:nowrap; }
.empty { color:var(--color-text-tertiary);font-size:0.75rem;padding:8px 0; }
</style>