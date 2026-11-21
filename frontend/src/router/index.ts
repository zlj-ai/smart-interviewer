import { createRouter, createWebHistory } from 'vue-router'

import { routes } from '@/constants/routes'
import { getAccessToken } from '@/api/request'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: routes.sessions },
    { path: routes.login, component: () => import('@/pages/LoginPage.vue') },
    { path: routes.register, component: () => import('@/pages/RegisterPage.vue') },
    {
      path: routes.sessions,
      component: () => import('@/pages/SessionListPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: routes.sessionDetail(0).replace('0', ':id'),
      component: () => import('@/pages/SessionDetailPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: routes.knowledge,
      component: () => import('@/pages/KnowledgePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: routes.resume,
      component: () => import('@/pages/ResumePage.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getAccessToken()) {
    return routes.login
  }
  return true
})
