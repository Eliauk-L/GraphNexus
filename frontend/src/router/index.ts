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
      path: '/settings',
      redirect: '/settings/fusion',
    },
    {
      path: '/settings/fusion',
      name: 'settings-fusion',
      component: () => import('@/views/fusion/FusionManagePage.vue'),
    },
    {
      path: '/settings/metrics',
      name: 'settings-metrics',
      component: () => import('@/views/metrics/MetricsDashboardPage.vue'),
    },
  ],
})

export default router