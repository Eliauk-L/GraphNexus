import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listGrades, listExams, deleteGradeByExam, uploadGradeFile } from '@/api/grade'
import type { GradeRecordVO, ExamSummaryVO } from '@/api/types'

export const useGradeStore = defineStore('grade', () => {
  const grades = ref<GradeRecordVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 考试汇总（管理考试弹窗）
  const exams = ref<ExamSummaryVO[]>([])
  const examsLoading = ref(false)

  // 筛选条件
  const filters = ref({
    examNo: '',
    examName: '',
    studentNo: '',
    name: '',
    className: '',
    subject: '',
  })

  async function loadGrades(pageNum = 1, pageSize = 20) {
    loading.value = true
    error.value = null
    try {
      const params: Record<string, string | number> = { pageNum, pageSize }
      for (const [k, v] of Object.entries(filters.value)) {
        if (v) params[k] = v
      }
      const result = await listGrades(params)
      grades.value = result.list
      total.value = result.total
    } catch {
      error.value = '加载成绩失败'
    } finally {
      loading.value = false
    }
  }

  /** 加载全量考试汇总（管理考试弹窗用） */
  async function loadExams() {
    examsLoading.value = true
    try {
      const result = await listExams({ pageSize: 1000 })
      exams.value = result.list
    } catch {
      error.value = '加载考试列表失败'
    } finally {
      examsLoading.value = false
    }
  }

  async function remove(examNo: string) {
    error.value = null
    try {
      await deleteGradeByExam(examNo)
      // 从本地考试列表中移除
      exams.value = exams.value.filter((e) => e.examNo !== examNo)
      await loadGrades()
    } catch {
      error.value = '删除失败'
      throw new Error('删除失败')
    }
  }

  async function upload(file: File, subject: string) {
    loading.value = true
    error.value = null
    try {
      await uploadGradeFile(file, subject)
      await loadGrades()
      await loadExams() // 刷新考试列表
    } catch {
      error.value = '上传成绩失败'
      throw new Error('上传成绩失败')
    } finally {
      loading.value = false
    }
  }

  return { grades, total, loading, error, exams, examsLoading, filters, loadGrades, loadExams, remove, upload }
})