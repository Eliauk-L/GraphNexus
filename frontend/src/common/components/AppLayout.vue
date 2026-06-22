<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterView, RouterLink, useRouter } from 'vue-router'
import {
  BookOpen, GitGraph, GraduationCap, MessageCircle,
  Settings, GitMerge, BarChart3, User, LogOut,
} from '@lucide/vue'
import { NPopover } from 'naive-ui'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const navItems = [
  { path: '/materials', label: '教材管理', icon: BookOpen, roles: ['ADMIN', 'TEACHER'] },
  { path: '/knowledge-graph', label: '知识点可视化', icon: GitGraph, roles: ['ADMIN', 'TEACHER', 'OPS_MANAGER'] },
  { path: '/grades', label: '学生成绩管理', icon: GraduationCap, roles: ['ADMIN', 'TEACHER'] },
  { path: '/diagnosis', label: '学情诊断', icon: MessageCircle, roles: ['ADMIN', 'TEACHER', 'STUDENT'] },
]

const settingsItems = [
  { path: '/settings/fusion', label: '融合管理', icon: GitMerge, roles: ['ADMIN', 'OPS_STAFF'] },
  { path: '/settings/metrics', label: '图指标', icon: BarChart3, roles: ['ADMIN', 'OPS_STAFF', 'OPS_MANAGER'] },
  { path: '/settings/users', label: '用户管理', icon: User, roles: ['ADMIN'] },
]

// 按角色过滤
const visibleNavItems = computed(() =>
  navItems.filter(i => i.roles.some(r => authStore.hasRole(r)))
)
const visibleSettingsItems = computed(() =>
  settingsItems.filter(i => i.roles.some(r => authStore.hasRole(r)))
)

const allItems = [...visibleNavItems.value, ...visibleSettingsItems.value]

const currentTitle = computed(() => {
  return allItems.find((i) => route.path.startsWith(i.path))?.label ?? 'GraphNexus'
})

function handleLogout() {
  // 先保存 refreshToken，logout() 会清除本地状态
  const rt = authStore.refreshToken
  authStore.logout()
  // 立即跳转登录页
  router.push('/login')
  // 跳转后再通知服务端（避免导航取消请求）
  if (rt) {
    import('@/api/auth').then(m => m.authApi.logout(rt).catch(() => {}))
  }
}
</script>

<template>
  <div class="shell">
    <!-- 侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar__brand display">
        GraphNexus
      </div>
      <nav class="sidebar__nav">
        <RouterLink
          v-for="item in visibleNavItems"
          :key="item.path"
          :to="item.path"
          class="sidebar__item"
          :class="{ active: route.path.startsWith(item.path) }"
        >
          <component :is="item.icon" :size="20" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <!-- 系统设置（左下角弱化） -->
      <div class="sidebar__bottom">
        <RouterLink
          v-for="item in visibleSettingsItems"
          :key="item.path"
          :to="item.path"
          class="sidebar__item sidebar__item--muted"
          :class="{ active: route.path.startsWith(item.path) }"
        >
          <component :is="item.icon" :size="16" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </div>
    </aside>

    <!-- 内容区 -->
    <div class="shell__main">
      <header class="topbar">
        <h2 class="title" style="margin: 0">{{ currentTitle }}</h2>
        <div class="topbar__user">
          <template v-if="authStore.isAuthenticated">
            <NPopover trigger="click" placement="bottom-end">
              <template #trigger>
                <div class="topbar__user-trigger">
                  <div class="avatar" :style="{ background: 'var(--color-brand-veil)' }">
                    <span class="label" style="color: var(--color-brand)">{{ authStore.userInitial }}</span>
                  </div>
                  <span class="supporting">{{ authStore.userName }}</span>
                </div>
              </template>
              <div style="padding: var(--spacing-sm); min-width: 160px">
                <div class="supporting" style="padding: 4px 8px">{{ authStore.userName }}</div>
                <div class="supporting" style="padding: 4px 8px; color: var(--color-text-tertiary); font-size: 12px">
                  {{ authStore.userInfo?.roles?.join(', ') }}
                </div>
                <div
                  style="margin-top: var(--spacing-sm); padding: 6px 8px; cursor: pointer;
                         border-radius: var(--rounded-sm); color: var(--color-text-secondary);
                         display: flex; align-items: center; gap: 6px"
                  class="logout-btn"
                  @click="handleLogout"
                >
                  <LogOut :size="14" />
                  <span class="supporting">登出</span>
                </div>
              </div>
            </NPopover>
          </template>
          <template v-else>
            <div class="avatar"><User :size="18" /></div>
          </template>
        </div>
      </header>
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
}

/* ── 侧边栏 ── */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  background: var(--color-sidebar-bg);
  display: flex;
  flex-direction: column;
}

.sidebar__brand {
  padding: var(--spacing-lg) var(--spacing-lg);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-brand);
  letter-spacing: -0.02em;
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 var(--spacing-sm);
  flex: 1;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 40px;
  padding: 0 var(--spacing-md);
  border-radius: var(--rounded-md);
  color: var(--color-sidebar-text);
  font-size: 0.875rem;
  font-weight: 400;
  text-decoration: none;
  transition: background var(--duration-fast) var(--ease-out);
  position: relative;
}

.sidebar__item:hover {
  background: oklch(1 0 0 / 0.08);
  color: var(--color-sidebar-text-active);
}

.sidebar__item.active {
  color: var(--color-sidebar-text-active);
  background: oklch(1 0 0 / 0.06);
}

.sidebar__item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: var(--color-brand);
  border-radius: 0 var(--rounded-sm) var(--rounded-sm) 0;
}

/* ── 系统设置（左下角弱化）── */
.sidebar__bottom {
  padding: var(--spacing-sm);
  border-top: 1px solid oklch(1 0 0 / 0.08);
  margin-top: auto;
}

.sidebar__item--muted {
  font-size: 0.8125rem;
  color: oklch(0.75 0.01 250 / 0.55);
  height: 36px;
}

.sidebar__item--muted:hover {
  color: oklch(0.75 0.01 250 / 0.8);
}

.sidebar__item--muted.active {
  color: var(--color-sidebar-text-active);
}

.sidebar__item--muted.active::before {
  width: 2px;
}

/* ── 主区域 ── */
.shell__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 var(--spacing-xl);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.topbar__user {
  display: flex;
  align-items: center;
}

.topbar__user-trigger {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--color-brand-veil);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-brand);
}

.content {
  flex: 1;
  padding: var(--spacing-xl);
  overflow-y: auto;
}
</style>