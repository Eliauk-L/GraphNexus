import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/files',
    },
    {
      path: '/files',
      name: 'files',
      component: () => import('@/views/file/FileManagePage.vue'),
    },
    {
      path: '/grades',
      name: 'grades',
      component: () => import('@/views/grade/GradeManagePage.vue'),
    },
    {
      path: '/qa',
      name: 'qa',
      component: () => import('@/views/query/IntelligentQAPage.vue'),
    },
    {
      path: '/graph',
      name: 'graph',
      component: () => import('@/views/graph/GraphVisualizePage.vue'),
    },
    {
      path: '/graph/document/:id',
      name: 'graph-document',
      component: () => import('@/views/graph/GraphVisualizePage.vue'),
    },
    {
      path: '/fusion',
      name: 'fusion',
      component: () => import('@/views/fusion/FusionManagePage.vue'),
    },
    {
      path: '/metrics',
      name: 'metrics',
      component: () => import('@/views/metrics/MetricsDashboardPage.vue'),
    },
  ],
})

export default router