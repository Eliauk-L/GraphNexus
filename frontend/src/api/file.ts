import client from './client'
import type { FileVO, ParseResultVO, UpdateFileRequest, PageResult } from './types'

/** 上传文件（PDF/TXT/CSV 统一入口） */
export function uploadFile(file: File, subject: string): Promise<FileVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('subject', subject)
  return client.post('/file/document/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 触发文档解析 */
export function processFile(id: number): Promise<ParseResultVO> {
  return client.post(`/file/document/${id}/process`)
}

/** 分页查询文件列表 */
export function listFiles(pageNum = 1, pageSize = 10, fileType?: string, name?: string): Promise<PageResult<FileVO>> {
  return client.get('/file/document', { params: { pageNum, pageSize, fileType, name } })
}

/** 查询单个文件 */
export function getFile(id: number): Promise<FileVO> {
  return client.get(`/file/document/${id}`)
}

/** 更新文件名称 */
export function updateFile(id: number, name: string): Promise<FileVO> {
  return client.put(`/file/document/${id}`, { name } satisfies UpdateFileRequest)
}

/** 删除文件 */
export function deleteFile(id: number): Promise<null> {
  return client.delete(`/file/document/${id}`)
}