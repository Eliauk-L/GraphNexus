<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterView, RouterLink } from 'vue-router'
import {
  FolderOpen, GitGraph, GraduationCap, MessageCircle,
  GitMerge, BarChart3, User,
} from '@lucide/vue'

const route = useRoute()

const navItems = [
  { path: '/files', label: '文件管理', icon: FolderOpen },
  { path: '/graph', label: '图谱可视化', icon: GitGraph },
  { path: '/grades', label: '成绩管理', icon: GraduationCap },
  { path: '/qa', label: '智能问答', icon: MessageCircle },
  { path: '/fusion', label: '融合管理', icon: GitMerge },
  { path: '/metrics', label: '图指标', icon: BarChart3 },
]

const currentTitle = computed(() => {
  return navItems.find((i) => route.path.startsWith(i.path))?.label ?? 'GraphNexus'
})
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
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="sidebar__item"
          :class="{ active: route.path.startsWith(item.path) }"
        >
          <component :is="item.icon" :size="20" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <!-- 内容区 -->
    <div class="shell__main">
      <header class="topbar">
        <h2 class="title" style="margin: 0">{{ currentTitle }}</h2>
        <div class="topbar__user">
          <div class="avatar">
            <User :size="18" />
          </div>
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