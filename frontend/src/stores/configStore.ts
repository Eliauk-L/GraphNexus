import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getConfigs, updateConfig as apiUpdateConfig, applyConfigs as apiApplyConfigs, type ConfigVO, type ApplyResultVO } from '@/api/config'

export const useConfigStore = defineStore('config', () => {
  // ── state ──
  const configs = ref<ConfigVO[]>([])
  const loading = ref(false)
  const applying = ref(false)
  const activeCategory = ref<'BUSINESS_PARAM' | 'LLM_PROMPT' | 'LLM_MODEL'>('BUSINESS_PARAM')

  // ── getters ──
  const configsByCategory = computed(() =>
    configs.value.filter(c => c.category === activeCategory.value)
  )

  const pendingCount = computed(() =>
    configs.value.filter(c => !c.applied).length
  )

  const hasPendingChanges = computed(() => pendingCount.value > 0)

  // ── actions ──
  async function loadConfigs() {
    loading.value = true
    try {
      configs.value = await getConfigs()
    } finally {
      loading.value = false
    }
  }

  async function updateConfig(configKey: string, configValue: string) {
    const updated = await apiUpdateConfig(configKey, configValue)
    // 局部更新列表中对应项
    const idx = configs.value.findIndex(c => c.configKey === configKey)
    if (idx !== -1) {
      configs.value[idx] = { ...configs.value[idx], ...updated, applied: false }
    }
    return updated
  }

  async function applyConfigs(): Promise<ApplyResultVO> {
    applying.value = true
    try {
      const result = await apiApplyConfigs()
      // 重新加载全量配置以同步 applied 标记
      await loadConfigs()
      return result
    } finally {
      applying.value = false
    }
  }

  return {
    // state
    configs,
    loading,
    applying,
    activeCategory,
    // getters
    configsByCategory,
    pendingCount,
    hasPendingChanges,
    // actions
    loadConfigs,
    updateConfig,
    applyConfigs,
  }
})