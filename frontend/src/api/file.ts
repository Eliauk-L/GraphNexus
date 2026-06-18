import client from './client'
import type { ApiResult, PageResult, DocumentVO, ParseResultVO, UpdateDocumentRequest, DeleteResultVO, GradeRecordVO, GradeUploadResultVO } from './types'

/** 上传文件（PDF/CSV 统一入口） */
export function uploadFile(file: File, subject: string): Promise<DocumentVO | GradeUploadResultVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('subject', subject)
  return client.post('/document/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 触发文档解析 */
export function processFile(id: number): Promise<ParseResultVO> {
  return client.post(`/document/${id}/process`)
}

/** 分页查询文件列表 */
export function listFiles(pageNum = 1, pageSize = 10): Promise<PageResult<DocumentVO>> {
  return client.get('/document', { params: { pageNum, pageSize } })
}

/** 查询单个文件 */
export function getFile(id: number): Promise<DocumentVO> {
  return client.get(`/document/${id}`)
}

/** 更新文件名称 */
export function updateFile(id: number, name: string): Promise<DocumentVO> {
  return client.put(`/document/${id}`, { name } satisfies UpdateDocumentRequest)
}

/** 删除文件 */
export function deleteFile(id: number): Promise<null> {
  return client.delete(`/document/${id}`)
}

/** 按考试编号查询成绩 */
export function queryGradeByExam(examNo: string): Promise<GradeRecordVO[]> {
  return client.get(`/document/grade/exam/${examNo}`)
}

/** 按考试编号删除成绩 */
export function deleteGradeByExam(examNo: string): Promise<DeleteResultVO> {
  return client.delete(`/document/grade/exam/${examNo}`)
}