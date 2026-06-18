import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryGradeByExam, deleteGradeByExam } from '@/api/file'
import type { GradeRecordVO } from '@/api/types'

export const useGradeStore = defineStore('grade', () => {
  const grades = ref<GradeRecordVO[]>([])
  const examNo = ref('')
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function searchExamNo(no: string) {
    loading.value = true
    examNo.value = no
    error.value = null
    try {
      grades.value = await queryGradeByExam(no)
    } catch (e) {
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
    } catch (e) {
      error.value = '删除失败'
      throw e
    }
  }

  return { grades, examNo, loading, error, searchExamNo, remove }
})