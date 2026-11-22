import { createRouter, createWebHistory } from 'vue-router'

import { routes } from '@/constants/routes'
import { getAccessToken } from '@/api/request'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: routes.sessions },
    {
      path: routes.login,
      name: 'login',
      component: () => import('@/pages/LoginPage.vue'),
    },
    {
      path: routes.register,
      name: 'register',
      component: () => import('@/pages/RegisterPage.vue'),
    },
    {
      path: routes.sessions,
      name: 'sessions',
      component: () => import('@/pages/SessionListPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: routes.sessionDetail(0).replace('0', ':id'),
      name: 'session-detail',
      component: () => import('@/pages/SessionDetailPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: routes.knowledge,
      name: 'knowledge',
      component: () => import('@/pages/KnowledgePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: routes.resume,
      name: 'resume',
      component: () => import('@/pages/ResumePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: routes.stats,
      name: 'stats',
      component: () => import('@/pages/StatsPage.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getAccessToken()) {
    return routes.login
  }
  // 已登录访问登录/注册页时重定向，避免重复登录
  if ((to.name === 'login' || to.name === 'register') && getAccessToken()) {
    return routes.sessions
  }
  return true
})
