import client from './client'
import type { GradeRecordVO, GradeUploadResultVO, ExamSummaryVO, DeleteResultVO, PageResult } from './types'

/** 上传成绩文件（CSV/Excel） */
export function uploadGradeFile(file: File, subject: string): Promise<GradeUploadResultVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('subject', subject)
  return client.post('/file/grades/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 条件组合查询学生成绩（所有参数可选，支持分页） */
export function listGrades(params: {
  examNo?: string
  examName?: string
  studentNo?: string
  name?: string
  className?: string
  subject?: string
  pageNum?: number
  pageSize?: number
} = {}): Promise<PageResult<GradeRecordVO>> {
  return client.get('/file/grades', { params })
}

/** 分页查询不重复的考试汇总（管理考试弹窗用） */
export function listExams(params: {
  pageNum?: number
  pageSize?: number
} = {}): Promise<PageResult<ExamSummaryVO>> {
  return client.get('/file/grades/exams', { params })
}

/** 按考试编号级联删除成绩 */
export function deleteGradeByExam(examNo: string): Promise<DeleteResultVO> {
  return client.delete(`/file/grades/exam/${examNo}`)
}