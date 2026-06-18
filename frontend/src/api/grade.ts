import client from './client'
import type { GradeRecordVO, GradeUploadResultVO, DeleteResultVO, PageResult } from './types'

/** 分页查询成绩列表（按考试分组） */
export function listGrades(pageNum = 1, pageSize = 10): Promise<PageResult<GradeUploadResultVO>> {
  return client.get('/file/grade', { params: { pageNum, pageSize } })
}

/** 按考试编号查询成绩 */
export function queryGradeByExam(examNo: string): Promise<GradeRecordVO[]> {
  return client.get(`/file/grade/exam/${examNo}`)
}

/** 按考试编号删除成绩 */
export function deleteGradeByExam(examNo: string): Promise<DeleteResultVO> {
  return client.delete(`/file/grade/exam/${examNo}`)
}