import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listGrades, queryGradeByExam, deleteGradeByExam } from '@/api/grade'
import type { GradeRecordVO, GradeUploadResultVO } from '@/api/types'

export const useGradeStore = defineStore('grade', () => {
  const grades = ref<GradeRecordVO[]>([])
  const exams = ref<GradeUploadResultVO[]>([])
  const examNo = ref('')
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function loadExams(pageNum = 1, pageSize = 10) {
    loading.value = true
    error.value = null
    try {
      const result = await listGrades(pageNum, pageSize)
      exams.value = result.list
    } catch {
      error.value = '加载成绩列表失败'
    } finally {
      loading.value = false
    }
  }

  async function searchExamNo(no: string) {
    loading.value = true
    examNo.value = no
    error.value = null
    try {
      grades.value = await queryGradeByExam(no)
    } catch {
      error.value = '查询成绩失败'
    } finally {
      loading.value = false
    }
  }

  async function remove(no: string) {
    error.value = null
    try {
      await deleteGradeByExam(no)
      grades.value = []
      examNo.value = ''
      await loadExams()
    } catch {
      error.value = '删除失败'
      throw new Error('删除失败')
    }
  }

  return { grades, exams, examNo, loading, error, loadExams, searchExamNo, remove }
})