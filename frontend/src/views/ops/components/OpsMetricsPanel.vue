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
    kpPR.value = top(a, 'KnowledgePoint'); stPR.value = top(b, 'Student')
    kpDeg.value = top(c, 'KnowledgePoint'); stDeg.value = top(d, 'Student')
  } catch (e: any) { error.value = e?.message ?? '加载失败' }
  finally { loading.value = false }
}

function top(list: MetricResultVO[], t: string): MetricRow[] {
  return (Array.isArray(list) ? list : [])
    .filter(m => m.nodeType === t)
    .sort((a, b) => b.metricValue - a.metricValue)
    .slice(0, 5)
    .map(m => ({ name: m.nodeName ?? m.nodeId, value: m.metricValue, nodeType: m.nodeType, subject: m.subject ?? '', className: m.className ?? '' }))
}

function fmt(v: number) { return v.toFixed(4) }

onMounted(load)
</script>

<template>
  <section class="panel">
    <h2 class="headline">图度量指标 <NTag size="tiny" :bordered="true" style="vertical-align:middle;margin-left:8px">Top 5</NTag></h2>
    <div v-if="error" class="supporting" style="color:var(--color-error)">{{ error }}</div>

    <div class="grid">
      <div class="col">
        <div class="col-title">PageRank · 知识点</div>
        <table v-if="kpPR.length">
          <tr v-for="(r,i) in kpPR" :key="i">
            <td class="rank">{{ i+1 }}</td>
            <td class="name">{{ r.name }}<span v-if="r.subject" class="sub">{{ r.subject }}</span></td>
            <td class="val mono">{{ fmt(r.value) }}</td>
          </tr>
        </table>
        <div v-else class="supporting" style="color:var(--color-text-tertiary)">—</div>
      </div>

      <div class="col">
        <div class="col-title">PageRank · 学生</div>
        <table v-if="stPR.length">
          <tr v-for="(r,i) in stPR" :key="i">
            <td class="rank">{{ i+1 }}</td>
            <td class="name">{{ r.name }}<span v-if="r.className" class="sub">{{ r.className }}</span></td>
            <td class="val mono">{{ fmt(r.value) }}</td>
          </tr>
        </table>
        <div v-else class="supporting" style="color:var(--color-text-tertiary)">—</div>
      </div>

      <div class="col">
        <div class="col-title">度中心性 · 知识点</div>
        <table v-if="kpDeg.length">
          <tr v-for="(r,i) in kpDeg" :key="i">
            <td class="rank">{{ i+1 }}</td>
            <td class="name">{{ r.name }}<span v-if="r.subject" class="sub">{{ r.subject }}</span></td>
            <td class="val mono">{{ r.value }}</td>
          </tr>
        </table>
        <div v-else class="supporting" style="color:var(--color-text-tertiary)">—</div>
      </div>

      <div class="col">
        <div class="col-title">度中心性 · 学生</div>
        <table v-if="stDeg.length">
          <tr v-for="(r,i) in stDeg" :key="i">
            <td class="rank">{{ i+1 }}</td>
            <td class="name">{{ r.name }}<span v-if="r.className" class="sub">{{ r.className }}</span></td>
            <td class="val mono">{{ r.value }}</td>
          </tr>
        </table>
        <div v-else class="supporting" style="color:var(--color-text-tertiary)">—</div>
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
td { padding:4px 0;font-size:0.8125rem;border-bottom:1px solid var(--color-border); }
tr:last-child td { border-bottom:none; }
.rank { width:18px;color:var(--color-text-tertiary);font-weight:500; }
.name { text-align:left;white-space:nowrap; }
.name .sub { display:block;font-size:0.6875rem;color:var(--color-text-tertiary); }
.val { text-align:right;white-space:nowrap;color:var(--color-text-primary); }
</style>