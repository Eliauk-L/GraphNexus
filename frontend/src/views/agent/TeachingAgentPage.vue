<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NAlert, NCollapse, NCollapseItem, NInput, NInputNumber, NProgress, NSpin, NTag, useMessage } from 'naive-ui'
import { Bot, Clock3, Route, Search, Sparkles } from '@lucide/vue'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseCard from '@/common/components/BaseCard.vue'
import MarkdownViewer from '@/common/components/MarkdownViewer.vue'
import { useAuthStore } from '@/stores/authStore'
import { useAgentStore } from './agentStore'

const auth = useAuthStore()
const store = useAgentStore()
const message = useMessage()
const question = ref('请分析我的薄弱知识点，并制定一周学习计划。')
const studentNo = ref(auth.hasRole('STUDENT') ? (auth.userInfo?.username ?? '') : '')
const subject = ref('数学')
const dailyMinutes = ref(45)
const days = ref(7)

const canSubmit = computed(() => question.value.trim() && studentNo.value.trim() && subject.value.trim())
const learningPath = computed(() => {
  const call = store.response?.trace.find(item => item.toolName === 'learning_path_recommendation')
  const data = call?.observation.data as { steps?: Array<Record<string, unknown>> } | undefined
  return data?.steps ?? []
})

async function ask() {
  if (!canSubmit.value) return
  try {
    await Promise.all([
      store.ask({ question: question.value, studentNo: studentNo.value, subject: subject.value,
        dailyMinutes: dailyMinutes.value, days: days.value }),
      store.loadMastery(studentNo.value, subject.value),
    ])
  } catch {
    message.error('Agent 执行失败，请检查输入或稍后重试')
  }
}

function masteryColor(weight: number) {
  if (weight < 0.6) return '#d94a4a'
  if (weight < 0.8) return '#d68a20'
  return '#2d9d78'
}

function displayName(tool: string) {
  return ({ knowledge_graph_search: '知识图谱检索', student_profile: '学生画像',
    weakness_analysis: '薄弱点分析', learning_path_recommendation: '学习路径推荐' } as Record<string, string>)[tool] ?? tool
}

function pretty(value: unknown) {
  return JSON.stringify(value, null, 2)
}

onMounted(() => store.loadTasks().catch(() => undefined))
</script>

<template>
  <div class="agent-page">
    <div class="hero">
      <div>
        <div class="eyebrow"><Sparkles :size="14" /> EVIDENCE-GROUNDED AGENT</div>
        <h1 class="headline">AI 教学辅助工作台</h1>
        <p class="supporting">结合知识图谱、考试证据与动态掌握度，生成可追溯的学情诊断和学习路径。</p>
      </div>
      <Bot :size="44" class="hero__icon" />
    </div>

    <div class="workspace">
      <section class="main-column">
        <BaseCard title="分析任务">
          <div class="form-grid">
            <label><span class="micro-label">学生学号</span><NInput v-model:value="studentNo" :disabled="auth.hasRole('STUDENT')" placeholder="例如 S001" /></label>
            <label><span class="micro-label">学科</span><NInput v-model:value="subject" placeholder="例如 数学" /></label>
            <label><span class="micro-label">每日分钟</span><NInputNumber v-model:value="dailyMinutes" :min="10" :max="240" /></label>
            <label><span class="micro-label">计划天数</span><NInputNumber v-model:value="days" :min="1" :max="30" /></label>
          </div>
          <label class="question-field"><span class="micro-label">教学问题</span><NInput v-model:value="question" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" /></label>
          <template #footer><BaseButton :disabled="!canSubmit || store.loading" :loading="store.loading" @click="ask"><Search :size="15" /> 开始分析</BaseButton></template>
        </BaseCard>

        <NSpin :show="store.loading">
          <BaseCard v-if="store.response" title="Agent 回答" class="result-card">
            <template #header><NTag :type="store.response.status === 'COMPLETED' ? 'success' : 'warning'">{{ store.response.status }}</NTag></template>
            <NAlert v-if="store.response.fallbackReason" type="warning" :show-icon="true" class="fallback-alert">已启用降级策略：{{ store.response.fallbackReason }}</NAlert>
            <MarkdownViewer :content="store.response.answer" :max-width="1000" />
            <div v-if="store.response.evidence.length" class="evidence-list">
              <span class="micro-label">证据引用</span>
              <NTag v-for="item in store.response.evidence" :key="item.type + item.id" size="small" :title="item.summary">{{ item.type }} · {{ item.id }}</NTag>
            </div>
          </BaseCard>
        </NSpin>

        <BaseCard v-if="learningPath.length" title="个性化学习路径">
          <div class="path-list">
            <div v-for="(task, index) in learningPath" :key="index" class="path-item">
              <div class="path-index">{{ index + 1 }}</div>
              <div><strong>第 {{ task.day ?? 1 }} 天 · {{ task.name ?? `学习任务 ${index + 1}` }}</strong><p class="supporting">{{ task.reason ?? '按前置依赖顺序完成学习与练习' }} · {{ task.successCriterion }}</p></div>
              <NTag size="small"><Clock3 :size="12" /> {{ task.plannedMinutes ?? dailyMinutes }} 分钟</NTag>
            </div>
          </div>
        </BaseCard>

        <BaseCard v-if="store.response?.trace.length" title="可审计执行轨迹">
          <NCollapse arrow-placement="right">
            <NCollapseItem v-for="call in store.response.trace" :key="call.round" :name="call.round">
              <template #header><span class="trace-title"><span class="trace-round">{{ call.round }}</span>{{ displayName(call.toolName) }}<NTag size="tiny" :type="call.observation.success ? 'success' : 'error'">{{ call.observation.metrics.elapsedMs }} ms</NTag></span></template>
              <p class="supporting decision">{{ call.decisionSummary }}</p>
              <div class="trace-grid"><div><span class="micro-label">输入</span><pre>{{ call.argumentsJson }}</pre></div><div><span class="micro-label">结构化观察</span><pre>{{ pretty(call.observation.data) }}</pre></div></div>
            </NCollapseItem>
          </NCollapse>
        </BaseCard>
      </section>

      <aside class="side-column">
        <BaseCard title="知识点掌握度">
          <NSpin :show="store.masteryLoading">
            <div v-if="store.mastery.length" class="mastery-list">
              <button v-for="item in store.mastery" :key="item.knowledgePointId" class="mastery-item" @click="store.loadHistory(studentNo, item)">
                <div class="mastery-label"><span>{{ item.knowledgePointName }}</span><strong>{{ Math.round(item.weight * 100) }}%</strong></div>
                <NProgress type="line" :percentage="Math.round(item.weight * 100)" :color="masteryColor(item.weight)" :show-indicator="false" />
                <span class="micro-label">{{ item.sampleCount }} 次证据 · 置信度 {{ Math.round(item.confidence * 100) }}%</span>
              </button>
            </div>
            <p v-else class="empty supporting">执行分析后显示动态掌握度。</p>
          </NSpin>
        </BaseCard>

        <BaseCard v-if="store.selectedKnowledgePoint" title="掌握度更新历史">
          <div class="history-list">
            <div v-for="item in store.masteryHistory" :key="item.eventId" class="history-item">
              <span class="history-dot" /><div><strong>{{ item.examNo }}</strong><p class="supporting">得分率 {{ Math.round(item.scoreRate * 100) }}% · 更新至 {{ Math.round(item.newWeight * 100) }}%</p></div>
            </div>
          </div>
        </BaseCard>

        <BaseCard title="最近任务">
          <button v-for="task in store.tasks.slice(0, 8)" :key="task.taskId" class="task-item" @click="store.loadTrace(task.taskId)">
            <Route :size="15" /><span><strong>{{ task.status }}</strong><small>{{ task.createTime?.replace('T', ' ').slice(0, 16) }}</small></span>
          </button>
          <p v-if="!store.tasks.length" class="empty supporting">暂无任务记录。</p>
          <div v-if="store.persistedTrace.length" class="persisted-summary supporting">所选任务共 {{ store.persistedTrace.length }} 次 Tool 调用</div>
        </BaseCard>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.agent-page { max-width: 1480px; margin: 0 auto; }
.hero { display: flex; justify-content: space-between; align-items: center; padding: 4px 4px var(--spacing-xl); }
.hero p { color: var(--color-text-secondary); margin-top: 6px; }
.eyebrow { display: flex; align-items: center; gap: 6px; color: var(--color-brand); font: 600 11px var(--font-display); letter-spacing: .12em; margin-bottom: 8px; }
.hero__icon { color: var(--color-brand); opacity: .8; }
.workspace { display: grid; grid-template-columns: minmax(0, 1fr) 350px; gap: var(--spacing-lg); align-items: start; }
.main-column, .side-column { display: grid; gap: var(--spacing-lg); }
.form-grid { display: grid; grid-template-columns: 1.5fr 1fr .8fr .8fr; gap: var(--spacing-md); }
label { display: grid; gap: 6px; }
.question-field { margin-top: var(--spacing-md); }
.result-card { border-top: 3px solid var(--color-brand); }
.fallback-alert { margin-bottom: var(--spacing-md); }
.evidence-list { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; margin-top: var(--spacing-lg); padding-top: var(--spacing-md); border-top: 1px solid var(--color-border); }
.path-list { display: grid; gap: 0; }
.path-item { display: grid; grid-template-columns: 32px 1fr auto; gap: var(--spacing-md); align-items: center; padding: var(--spacing-md) 0; border-bottom: 1px solid var(--color-border); }
.path-item:last-child { border-bottom: 0; }
.path-index { width: 28px; height: 28px; display: grid; place-items: center; border-radius: 50%; background: var(--color-brand-veil); color: var(--color-brand); font-weight: 700; }
.trace-title { display: flex; align-items: center; gap: 9px; font-weight: 600; }
.trace-round { width: 22px; height: 22px; display: grid; place-items: center; border-radius: 6px; background: var(--color-bg); font: 700 12px var(--font-mono); }
.decision { margin: 0 0 var(--spacing-sm) 31px; }
.trace-grid { display: grid; grid-template-columns: 1fr 1.4fr; gap: var(--spacing-md); }
pre { max-height: 280px; overflow: auto; white-space: pre-wrap; margin-top: 6px; padding: var(--spacing-sm); background: var(--color-bg); border-radius: var(--rounded-sm); font: 12px/1.5 var(--font-mono); }
.mastery-list { display: grid; gap: 5px; }
.mastery-item { border: 0; background: transparent; text-align: left; padding: 9px 0; cursor: pointer; border-bottom: 1px solid var(--color-border); }
.mastery-item:hover { color: var(--color-brand); }
.mastery-label { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 5px; }
.mastery-item .micro-label { display: block; margin-top: 4px; color: var(--color-text-tertiary); }
.history-list { display: grid; }
.history-item { display: grid; grid-template-columns: 12px 1fr; gap: 9px; padding: 8px 0; }
.history-dot { width: 8px; height: 8px; margin-top: 7px; border-radius: 50%; background: var(--color-brand); box-shadow: 0 0 0 4px var(--color-brand-veil); }
.task-item { width: 100%; display: flex; gap: 9px; align-items: center; border: 0; border-bottom: 1px solid var(--color-border); background: transparent; padding: 10px 0; cursor: pointer; text-align: left; }
.task-item span { display: grid; } .task-item small { color: var(--color-text-tertiary); }
.persisted-summary { margin-top: 10px; color: var(--color-brand); }
.empty { padding: var(--spacing-lg) 0; text-align: center; color: var(--color-text-tertiary); }
@media (max-width: 1050px) { .workspace { grid-template-columns: 1fr; } .side-column { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) { .form-grid, .trace-grid, .side-column { grid-template-columns: 1fr; } }
</style>
