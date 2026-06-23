import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/materials',
    },
    {
      path: '/materials',
      name: 'materials',
      component: () => import('@/views/file/FileManagePage.vue'),
    },
    {
      path: '/knowledge-graph',
      name: 'knowledge-graph',
      component: () => import('@/views/graph/GraphVisualizePage.vue'),
    },
    {
      path: '/knowledge-graph/document/:id',
      name: 'knowledge-graph-document',
      component: () => import('@/views/graph/GraphVisualizePage.vue'),
    },
    {
      path: '/grades',
      name: 'grades',
      component: () => import('@/views/grade/GradeManagePage.vue'),
    },
    {
      path: '/diagnosis',
      name: 'diagnosis',
      component: () => import('@/views/query/IntelligentQAPage.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginPage.vue'),
      meta: { guest: true },
    },
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/auth/ForbiddenPage.vue'),
    },
    {
      path: '/settings/users',
      name: 'settings-users',
      component: () => import('@/views/auth/UserManagePage.vue'),
      meta: { roles: ['ADMIN'] },
    },
  ],
})

export default router