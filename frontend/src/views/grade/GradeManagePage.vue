<script setup lang="ts">
import { ref, h } from 'vue'
import { NSpace, useMessage } from 'naive-ui'
import { Search } from '@lucide/vue'
import { useGradeStore } from './gradeStore'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseInput from '@/common/components/BaseInput.vue'
import DataTable from '@/common/components/DataTable.vue'
import type { DataTableColumns } from 'naive-ui'

const store = useGradeStore()
const message = useMessage()
const searchExamNo = ref('')

const columns: DataTableColumns<any> = [
  { title: '学号', key: 'studentNo', width: 120 },
  { title: '姓名', key: 'name', width: 100 },
  { title: '班级', key: 'className', width: 120 },
  { title: '总分', key: 'totalScore', width: 80 },
  { title: '排名', key: 'classRank', width: 80 },
  {
    title: '得分明细', key: 'scoreDetails', ellipsis: { tooltip: true },
    render(row) { return typeof row.scoreDetails === 'string' ? row.scoreDetails : JSON.stringify(row.scoreDetails) },
  },
]

async function handleSearch() {
  if (!searchExamNo.value.trim()) return
  await store.searchExamNo(searchExamNo.value.trim())
}

async function handleDelete() {
  try {
    await store.remove(store.examNo)
    message.success('删除成功')
  } catch {
    // handled by store
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h1 class="headline">成绩管理</h1>
    </div>

    <div class="search-bar">
      <BaseInput
        v-model="searchExamNo"
        placeholder="输入考试编号，如 E20200041"
        style="width: 280px"
        @keyup.enter="handleSearch"
      />
      <BaseButton @click="handleSearch">
        <Search :size="16" style="margin-right: 4px" />
        查询
      </BaseButton>
      <BaseButton
        v-if="store.grades.length > 0"
        variant="danger"
        @click="handleDelete"
        style="margin-left: auto"
      >
        删除此考试
      </BaseButton>
    </div>

    <!-- 考试信息摘要 -->
    <div v-if="store.grades.length > 0" class="exam-info supporting" style="color: var(--color-text-secondary)">
      考试 {{ store.examNo }} | {{ store.grades[0].examName }} | 学科: {{ store.grades[0].subject }} | 共 {{ store.grades.length }} 名考生
    </div>

    <DataTable
      :columns="columns"
      :data="store.grades"
      :loading="store.loading"
      empty-text="请先输入考试编号查询"
    />
  </div>
</template>

<style scoped>
.page-header {
  margin-bottom: var(--spacing-lg);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.exam-info {
  margin-bottom: var(--spacing-md);
}
</style>