<script setup lang="ts">
import { onMounted } from 'vue'
import { NSpace } from 'naive-ui'
import { RefreshCw } from '@lucide/vue'
import { useFusionStore } from './fusionStore'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseCard from '@/common/components/BaseCard.vue'

const store = useFusionStore()

onMounted(() => {
  store.loadStatus()
})

function handleExecute() {
  store.execute()
}

function handleRollback() {
  if (store.lastStatus) {
    store.rollback(store.lastStatus.fusionLogId)
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">融合管理</h1>
    </div>

    <BaseCard style="margin-bottom: var(--spacing-lg)">
      <template #header>
        <div class="fusion-action">
          <BaseButton
            :disabled="store.executing"
            @click="handleExecute"
          >
            <RefreshCw :size="16" :class="{ spin: store.executing }" style="margin-right: 4px" />
            {{ store.executing ? '融合执行中...' : '执行全量融合' }}
          </BaseButton>
          <span v-if="store.lastStatus && !store.lastStatus.rolledBack">
            <BaseButton variant="danger" @click="handleRollback" style="margin-left: 8px">
              回滚最近融合
            </BaseButton>
          </span>
        </div>
      </template>
    </BaseCard>

    <!-- 融合状态 -->
    <BaseCard v-if="store.lastStatus" title="最近融合状态">
      <div class="fusion-status-grid supporting">
        <div class="status-item">
          <span class="label">日志 ID</span>
          <span>{{ store.lastStatus.fusionLogId }}</span>
        </div>
        <div class="status-item">
          <span class="label">触发方式</span>
          <span>{{ store.lastStatus.triggerType === 'MANUAL' ? '手动' : '自动增量' }}</span>
        </div>
        <div class="status-item">
          <span class="label">执行时间</span>
          <span>{{ store.lastStatus.executedAt?.replace('T', ' ') }}</span>
        </div>
        <div class="status-item">
          <span class="label">合并 KP 组数</span>
          <span>{{ store.lastStatus.mergedKpGroupCount }}</span>
        </div>
        <div class="status-item">
          <span class="label">MASTERS 边数</span>
          <span>{{ store.lastStatus.mastersEdgeCount }}</span>
        </div>
        <div class="status-item">
          <span class="label">状态</span>
          <span :style="{ color: store.lastStatus.rolledBack ? 'var(--color-warning)' : 'var(--color-success)' }">
            {{ store.lastStatus.rolledBack ? '已回滚' : '已完成' }}
          </span>
        </div>
      </div>
    </BaseCard>

    <div v-else-if="!store.loading" class="empty body" style="color: var(--color-text-tertiary)">
      暂无融合记录，点击上方按钮执行首次融合
    </div>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: var(--spacing-lg); }
.fusion-action { display: flex; align-items: center; }

.fusion-status-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
}

.status-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-item .label {
  font-weight: 500;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-tertiary);
}

.empty {
  display: flex;
  justify-content: center;
  padding: var(--spacing-3xl) 0;
}

.spin { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>