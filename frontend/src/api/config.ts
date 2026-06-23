import client from './client'

/** 配置项 VO（对齐后端 ConfigController.list() 返回的 Map） */
export interface ConfigVO {
  configKey: string
  configName: string
  configValue: string | null
  configType: 'NUMBER' | 'STRING' | 'BOOLEAN' | 'TEXT'
  category: 'BUSINESS_PARAM' | 'LLM_PROMPT' | 'LLM_MODEL'
  description: string
  defaultValue: string | null
  required: boolean
  applied: boolean
  isCustomized: boolean
  validationRule: string | null
}

/** Apply 操作响应 */
export interface ApplyResultVO {
  reloadedCount: number
  reloadedAt: string
}

/**
 * 获取所有配置列表（按 category + sortOrder 分组排序）。
 */
export function getConfigs(): Promise<ConfigVO[]> {
  return client.get('/config')
}

/**
 * 获取单个配置项详情。
 */
export function getConfig(configKey: string): Promise<ConfigVO> {
  return client.get(`/config/${encodeURIComponent(configKey)}`)
}

/**
 * 更新配置值（保存到 DB，不 Apply）。
 */
export function updateConfig(configKey: string, configValue: string): Promise<ConfigVO> {
  return client.put(`/config/${encodeURIComponent(configKey)}`, { configValue })
}

/**
 * 应用配置（从 DB 重载到运行中 Properties Bean）。
 */
export function applyConfigs(): Promise<ApplyResultVO> {
  return client.post('/config/apply')
}

/**
 * 获取配置的生效内容 — 用于编辑器预填。
 * TEXT 类型返回 classpath 原文，其他类型返回 configValue ?? defaultValue。
 */
export function getEffectiveContent(configKey: string): Promise<string> {
  return client.get(`/config/${encodeURIComponent(configKey)}/effective`)
}