import client from '@/api/client'
import type { FusionExecuteVO, FusionStatusVO, FusionRollbackVO } from '@/api/types'

/** 手动全量融合 */
export function executeFusion(): Promise<FusionExecuteVO> {
  return client.post('/analysis/fusion/execute')
}

/** 查询融合状态 */
export function getFusionStatus(): Promise<FusionStatusVO | null> {
  return client.get('/analysis/fusion/status')
}

/** 回滚融合 */
export function rollbackFusion(fusionLogId: number): Promise<FusionRollbackVO> {
  return client.post(`/analysis/fusion/rollback/${fusionLogId}`)
}
