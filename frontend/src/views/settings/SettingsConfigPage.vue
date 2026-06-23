<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NTabs, NTabPane, NModal, NInput, NInputNumber, NSwitch, NTag,
  NSpace, NEmpty, useMessage,
} from 'naive-ui'
import { Pencil } from '@lucide/vue'
import BaseCard from '@/common/components/BaseCard.vue'
import BaseButton from '@/common/components/BaseButton.vue'
import { useConfigStore } from '@/stores/configStore'
import { getEffectiveContent, type ConfigVO } from '@/api/config'

const message = useMessage()
const configStore = useConfigStore()

// ── edit modal ──
const showEditModal = ref(false)
const editingConfig = ref<ConfigVO | null>(null)
const editValue = ref<string | number | boolean>('')
const editLoading = ref(false)

// ── lifecycle ──
onMounted(() => {
  configStore.loadConfigs().catch(() => message.error('加载配置失败'))
})

// ── edit logic ──
async function openEdit(config: ConfigVO) {
  editingConfig.value = config
  showEditModal.value = true

  try {
    const content = await getEffectiveContent(config.configKey)
    if (config.configType === 'NUMBER') {
      editValue.value = content ? Number(content) : 0
    } else if (config.configType === 'BOOLEAN') {
      editValue.value = content === 'true'
    } else {
      editValue.value = content ?? ''
    }
  } catch {
    if (config.configType === 'NUMBER') {
      editValue.value = config.configValue ? Number(config.configValue) : 0
    } else if (config.configType === 'BOOLEAN') {
      editValue.value = config.configValue === 'true'
    } else {
      editValue.value = config.configValue ?? ''
    }
  }
}

async function handleSave() {
  if (!editingConfig.value) return
  editLoading.value = true
  try {
    let valueStr: string
    if (editingConfig.value.configType === 'BOOLEAN') {
      valueStr = editValue.value ? 'true' : 'false'
    } else {
      valueStr = String(editValue.value)
    }
    await configStore.updateConfig(editingConfig.value.configKey, valueStr)
    message.success('配置已保存，请点击「应用配置」使其生效')
    showEditModal.value = false
  } catch (e: any) {
    message.error(e?.response?.data?.userTip || '保存失败')
  } finally {
    editLoading.value = false
  }
}

// ── apply logic ──
async function handleApply() {
  try {
    const result = await configStore.applyConfigs()
    message.success(`配置已应用，共更新 ${result.reloadedCount} 项`)
  } catch (e: any) {
    message.error(e?.response?.data?.userTip || '应用配置失败')
  }
}

// ── helpers ──
function displayValue(config: ConfigVO): string {
  if (config.configValue != null && config.configValue !== '') {
    if (config.configType === 'TEXT') {
      return config.configValue.length > 50
        ? config.configValue.substring(0, 50) + '…'
        : config.configValue
    }
    return config.configValue
  }
  return ''
}

function isUsingDefault(config: ConfigVO): boolean {
  return config.configValue == null || config.configValue === ''
}

function parseValidationRule(config: ConfigVO): { min?: number; max?: number } {
  if (!config.validationRule) return {}
  try {
    return JSON.parse(config.validationRule)
  } catch {
    return {}
  }
}

const tabOptions = [
  { label: '业务参数', value: 'BUSINESS_PARAM' as const },
  { label: '提示词模板', value: 'LLM_PROMPT' as const },
  { label: '模型配置', value: 'LLM_MODEL' as const },
]
</script>

<template>
  <div class="config-page">
    <h1 class="headline" style="margin-bottom: var(--spacing-lg)">系统配置</h1>

    <NTabs
      v-model:value="configStore.activeCategory"
      type="line"
      :options="tabOptions"
      style="margin-bottom: var(--spacing-md)"
    >
      <NTabPane v-for="tab in tabOptions" :key="tab.value" :name="tab.value" />
    </NTabs>

    <BaseCard>
      <NEmpty
        v-if="!configStore.loading && configStore.configsByCategory.length === 0"
        description="暂无配置数据"
      />

      <div v-else>
        <div
          v-for="config in configStore.configsByCategory"
          :key="config.configKey"
          class="config-row"
          :class="{ 'config-row--pending': !config.applied }"
        >
          <div v-if="!config.applied" class="config-row__pending-bar" />

          <div class="config-row__info">
            <span class="config-row__name">{{ config.configName }}</span>
            <span v-if="config.description" class="config-row__desc supporting">
              {{ config.description }}
            </span>
          </div>

          <div class="config-row__right">
            <!-- 使用默认值 -->
            <span v-if="isUsingDefault(config)" class="config-row__value" style="color: var(--color-text-tertiary)">
              {{ config.defaultValue ?? '—' }}
              <span class="config-row__default-tag">默认</span>
            </span>
            <!-- 自定义值 -->
            <span v-else class="config-row__value mono">{{ displayValue(config) }}</span>
            <NTag
              v-if="!config.applied"
              type="warning"
              size="tiny"
              :bordered="false"
              style="border-radius: var(--rounded-sm)"
            >
              未应用
            </NTag>
            <span class="config-row__edit" @click="openEdit(config)">
              <Pencil :size="14" />
            </span>
          </div>
        </div>
      </div>
    </BaseCard>

    <div style="height: 80px" />

    <div class="config-page__bottom-bar">
      <NTag
        v-if="configStore.hasPendingChanges"
        type="warning"
        size="small"
        round
        :bordered="false"
      >
        {{ configStore.pendingCount }} 项待应用
      </NTag>
      <div v-else />

      <BaseButton
        variant="primary"
        :loading="configStore.applying"
        :disabled="!configStore.hasPendingChanges"
        @click="handleApply"
      >
        应用配置
      </BaseButton>
    </div>

    <!-- 编辑弹窗 -->
    <NModal
      v-model:show="showEditModal"
      :title="editingConfig ? `编辑：${editingConfig.configName}` : ''"
      style="width: 520px"
      preset="card"
    >
      <div v-if="editingConfig" class="edit-form">
        <div class="edit-form__meta supporting">
          <div v-if="isUsingDefault(editingConfig)">
            当前使用：<span style="color: var(--color-text-tertiary)">系统默认值</span>
          </div>
          <div v-else>
            当前自定义值：<span class="mono">{{ editingConfig.configValue }}</span>
          </div>
          <div v-if="editingConfig.defaultValue" style="color: var(--color-text-tertiary)">
            系统默认值：{{ editingConfig.defaultValue }}
          </div>
          <div v-if="editingConfig.description" style="color: var(--color-text-tertiary); margin-top: var(--spacing-sm)">
            {{ editingConfig.description }}
          </div>
        </div>

        <div class="edit-form__input">
          <span class="supporting" style="display: block; margin-bottom: var(--spacing-sm); font-weight: 500">新值</span>

          <NInputNumber
            v-if="editingConfig.configType === 'NUMBER'"
            v-model:value="editValue as number"
            style="width: 100%"
            v-bind="parseValidationRule(editingConfig)"
          />

          <NSwitch
            v-else-if="editingConfig.configType === 'BOOLEAN'"
            :value="editValue as boolean"
            @update:value="(v: boolean) => (editValue = v)"
          />

          <NInput
            v-else-if="editingConfig.configType === 'TEXT'"
            v-model:value="editValue as string"
            type="textarea"
            :autosize="{ minRows: 15, maxRows: 30 }"
            style="font-family: var(--font-mono)"
          />

          <NInput
            v-else
            v-model:value="editValue as string"
            style="width: 100%"
          />
        </div>
      </div>

      <template #footer>
        <NSpace justify="end">
          <BaseButton variant="danger" @click="showEditModal = false">取消</BaseButton>
          <BaseButton variant="primary" :loading="editLoading" @click="handleSave">保存</BaseButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.config-page {
  position: relative;
  min-height: calc(100vh - 120px);
}

.config-row {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: var(--spacing-sm) 0;
  border-bottom: 1px solid var(--color-border);
  position: relative;
}
.config-row:last-child {
  border-bottom: none;
}

.config-row--pending {
  padding-left: var(--spacing-sm);
}

.config-row__pending-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--color-warning);
  border-radius: var(--rounded-full);
}

.config-row__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.config-row__name {
  font-size: 0.9375rem;
  color: var(--color-text-primary);
}

.config-row__desc {
  color: var(--color-text-tertiary);
}

.config-row__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-left: var(--spacing-lg);
}

.config-row__value {
  color: var(--color-text-secondary);
  font-size: 0.875rem;
}

.config-row__default-tag {
  display: inline-block;
  font-family: var(--font-body);
  font-size: 0.6875rem;
  color: var(--color-text-tertiary);
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--rounded-sm);
  padding: 0 4px;
  margin-left: 4px;
  vertical-align: middle;
}

.config-row__edit {
  color: var(--color-text-tertiary);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--rounded-sm);
  transition: color var(--duration-fast) var(--ease-out);
}
.config-row__edit:hover {
  color: var(--color-brand);
}

.config-page__bottom-bar {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.edit-form__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--rounded-sm);
}

.edit-form__input {
  padding-top: var(--spacing-sm);
}
</style>