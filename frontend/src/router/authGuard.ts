import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

export function registerAuthGuard(router: Router) {
  router.beforeEach((to, _from, next) => {
    const authStore = useAuthStore()

    authStore.restoreSession()

    const authed = authStore.isAuthenticated
    const userInfo = authStore.userInfo
    const roles = userInfo?.roles

    console.log('[Guard]', to.path, {
      authed,
      hasUserInfo: !!userInfo,
      roles: roles,
      metaRoles: to.meta.roles,
    })

    // 已登录用户访问 /login → 跳首页
    if (to.meta.guest && authed) {
      return next('/materials')
    }

    // 未登录用户访问非 guest 页面 → 跳登录
    if (!to.meta.guest && !authed) {
      console.log('[Guard] → /login (not authenticated)')
      return next({ path: '/login', query: { redirect: to.fullPath } })
    }

    // 有角色限制的页面
    if (to.meta.roles && Array.isArray(to.meta.roles)) {
      const required = to.meta.roles as string[]
      const hasPermission = required.some((r: string) => authStore.hasRole(r))
      if (!hasPermission) {
        console.log('[Guard] → /403', { required, userRoles: roles })
        return next('/403')
      }
    }

    next()
  })
}