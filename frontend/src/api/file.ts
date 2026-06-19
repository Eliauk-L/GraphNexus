import client from './client'
import type { TextbookVO, TextbookParseResultVO, PageResult } from './types'

/** 上传文档文件（PDF/TXT） */
export function uploadFile(file: File, subject: string): Promise<TextbookVO> {
  const form = new FormData()
  form.append('file', file)
  form.append('subject', subject)
  return client.post('/file/textbooks/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 触发文档解析 */
export function processFile(id: number): Promise<TextbookParseResultVO> {
  return client.post(`/file/textbooks/${id}/process`)
}

/** 分页查询文件列表 */
export function listFiles(pageNum = 1, pageSize = 10, fileType?: string, name?: string): Promise<PageResult<TextbookVO>> {
  return client.get('/file/textbooks', { params: { pageNum, pageSize, fileType, name } })
}

/** 查询单个文件 */
export function getFile(id: number): Promise<TextbookVO> {
  return client.get(`/file/textbooks/${id}`)
}

/** 删除文件 */
export function deleteFile(id: number): Promise<null> {
  return client.delete(`/file/textbooks/${id}`)
}