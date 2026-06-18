import client from './client'
import type { GradeRecordVO, GradeUploadResultVO, DeleteResultVO, PageResult } from './types'

/** 上传 CSV 成绩文件 */
export function uploadGradeFile(file: File, subject: string): Promise<GradeUploadResultVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('subject', subject)
  return client.post('/file/grades/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 分页查询成绩列表（按考试分组） */
export function listGrades(pageNum = 1, pageSize = 10): Promise<PageResult<GradeUploadResultVO>> {
  return client.get('/file/grade', { params: { pageNum, pageSize } })
}

/** 按考试编号查询成绩 */
export function queryGradeByExam(examNo: string): Promise<GradeRecordVO[]> {
  return client.get(`/file/grades/exam/${examNo}`)
}

/** 按考试编号删除成绩 */
export function deleteGradeByExam(examNo: string): Promise<DeleteResultVO> {
  return client.delete(`/file/grades/exam/${examNo}`)
}