import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

export function registerAuthGuard(router: Router) {
  router.beforeEach((to, _from, next) => {
    const authStore = useAuthStore()

    // 每次导航都从 localStorage 恢复，确保护栏看到的是最新状态
    authStore.restoreSession()

    // 已登录用户访问 /login → 跳首页
    if (to.meta.guest && authStore.isAuthenticated) {
      return next('/materials')
    }

    // 未登录用户访问非 guest 页面 → 跳登录
    if (!to.meta.guest && !authStore.isAuthenticated) {
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }

    // 有角色限制的页面
    if (to.meta.roles && Array.isArray(to.meta.roles)) {
      const required = to.meta.roles as string[]
      const hasPermission = required.some((r: string) => authStore.hasRole(r))
      if (!hasPermission) {
        return next('/403')
      }
    }

    next()
  })
}