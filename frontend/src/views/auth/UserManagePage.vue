<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import {
  NDataTable, NPagination, NModal, NInput, NSelect, NSwitch, NTag,
  NSpace, useMessage,
} from 'naive-ui'
import BaseCard from '@/common/components/BaseCard.vue'
import BaseButton from '@/common/components/BaseButton.vue'
import { authApi, type UserVO, type RoleVO } from '@/api/auth'

const message = useMessage()

// ── state ──
const users = ref<UserVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const showModal = ref(false)
const isEdit = ref(false)
const editingUser = ref<UserVO | null>(null)
const formUsername = ref('')
const formRealName = ref('')
const formPassword = ref('')
const formRoles = ref<string[]>([])
const formStatus = ref(true)
const formLoading = ref(false)
const allRoles = ref<RoleVO[]>([])

const roleOptions = ref<Array<{ label: string; value: string }>>([])

// ── role color map ──
const roleColorMap: Record<string, string> = {
  ADMIN: '#var(--color-brand)',
  TEACHER: '#var(--color-success)',
  STUDENT: '#var(--color-text-tertiary)',
  OPS_STAFF: '#var(--color-warning)',
  OPS_MANAGER: 'oklch(0.55 0.12 300)',
}
function roleColor(code: string): string {
  return roleColorMap[code] || 'var(--color-text-tertiary)'
}
function roleName(code: string): string {
  return allRoles.value.find(r => r.code === code)?.name ?? code
}

// ── load ──
async function loadUsers() {
  loading.value = true
  try {
    const res = await authApi.getUsers(pageNum.value, pageSize.value)
    users.value = res.data.data?.list ?? []
    total.value = res.data.data?.total ?? 0
  } catch { message.error('加载用户列表失败') }
  finally { loading.value = false }
}

async function loadRoles() {
  try {
    const res = await authApi.getRoles()
    allRoles.value = res.data.data ?? []
    roleOptions.value = allRoles.value.map(r => ({ label: r.name, value: r.code }))
  } catch {}
}

onMounted(() => { loadRoles(); loadUsers() })

// ── modal ──
function openCreate() {
  isEdit.value = false
  editingUser.value = null
  formUsername.value = ''
  formRealName.value = ''
  formPassword.value = ''
  formRoles.value = []
  formStatus.value = true
  showModal.value = true
}

function openEdit(user: UserVO) {
  isEdit.value = true
  editingUser.value = user
  formUsername.value = user.username
  formRealName.value = user.realName ?? ''
  formPassword.value = ''
  formRoles.value = [...user.roles]
  formStatus.value = user.status === 'ENABLED'
  showModal.value = true
}

async function handleSubmit() {
  formLoading.value = true
  try {
    const data = {
      realName: formRealName.value,
      roles: formRoles.value,
      status: formStatus.value ? 'ENABLED' : 'DISABLED',
      ...(isEdit.value ? {} : { username: formUsername.value, password: formPassword.value }),
    }
    if (isEdit.value && formPassword.value) {
      (data as any).password = formPassword.value
    }
    if (isEdit.value) {
      await authApi.updateUser(editingUser.value!.id, data)
      message.success('用户已更新')
    } else {
      await authApi.createUser({ username: formUsername.value, password: formPassword.value, realName: formRealName.value, roles: formRoles.value } as any)
      message.success('用户已创建')
    }
    showModal.value = false
    loadUsers()
  } catch (e: any) {
    message.error(e?.response?.data?.userTip || '操作失败')
  } finally { formLoading.value = false }
}

function onPageChange(p: number) { pageNum.value = p; loadUsers() }

// ── columns ──
const columns = [
  { title: '用户名', key: 'username', width: 120 },
  { title: '真实姓名', key: 'realName', width: 100 },
  {
    title: '角色', key: 'roles', width: 200,
    render(row: UserVO) {
      return h(NSpace, { size: 'small' }, () =>
        row.roles?.map(r => h(NTag, {
          size: 'small',
          style: { border: 'none', borderRadius: 'var(--rounded-sm)', color: roleColor(r), background: roleColor(r).replace(')', ' / 0.12)').replace('oklch', 'oklch') },
        }, () => roleName(r))) ?? []
      )
    },
  },
  {
    title: '状态', key: 'status', width: 80,
    render(row: UserVO) {
      const enabled = row.status === 'ENABLED'
      return h(NTag, {
        size: 'small',
        type: enabled ? 'success' : 'error',
        style: { border: 'none', borderRadius: 'var(--rounded-sm)' },
      }, () => enabled ? '启用' : '禁用')
    },
  },
  { title: '创建时间', key: 'createTime', width: 160 },
  {
    title: '操作', key: 'action', width: 80,
    render(row: UserVO) {
      return h('span', { style: { color: 'var(--color-brand)', cursor: 'pointer' }, onClick: () => openEdit(row) }, '编辑')
    },
  },
]
</script>

<template>
  <BaseCard title="用户管理">
    <template #header>
      <BaseButton variant="primary" @click="openCreate">+ 新建用户</BaseButton>
    </template>

    <NDataTable
      :columns="columns"
      :data="users"
      :loading="loading"
      :row-key="(r: UserVO) => r.id"
      :bordered="false"
    />

    <div style="display: flex; justify-content: flex-end; margin-top: var(--spacing-md)">
      <NPagination
        :page="pageNum"
        :page-size="pageSize"
        :item-count="total"
        :on-update:page="onPageChange"
      />
    </div>
  </BaseCard>

  <!-- 新建/编辑弹窗 -->
  <NModal
    v-model:show="showModal"
    :title="isEdit ? `编辑用户 — ${editingUser?.username}` : '新建用户'"
    style="width: 480px"
    preset="card"
  >
    <div style="display: flex; flex-direction: column; gap: var(--spacing-md)">
      <NInput v-model:value="formUsername" placeholder="用户名" :disabled="isEdit" />
      <NInput v-model:value="formRealName" placeholder="真实姓名" />
      <NInput v-model:value="formPassword" type="password" :placeholder="isEdit ? '留空则不修改密码' : '密码'" />
      <NSelect
        v-model:value="formRoles"
        :options="roleOptions"
        multiple
        placeholder="选择角色"
      />
      <div style="display: flex; align-items: center; gap: var(--spacing-sm)">
        <span class="supporting">状态</span>
        <NSwitch v-model:value="formStatus" :checked-value="true" :unchecked-value="false" />
        <span class="supporting" style="color: var(--color-text-tertiary)">
          {{ formStatus ? '启用' : '禁用' }}
        </span>
      </div>
    </div>

    <template #footer>
      <NSpace justify="end">
        <BaseButton variant="danger" @click="showModal = false">取消</BaseButton>
        <BaseButton variant="primary" :loading="formLoading" @click="handleSubmit">
          {{ isEdit ? '保存' : '确认创建' }}
        </BaseButton>
      </NSpace>
    </template>
  </NModal>
</template>