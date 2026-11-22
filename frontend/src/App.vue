<script setup lang="ts">
import { BookOpen, Bot, FileText, LogOut, MessagesSquare, TrendingUp } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

import { logout } from '@/api/auth'
import { routes } from '@/constants/routes'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const NAV_ITEMS = [
  { to: routes.sessions, label: '面试', icon: MessagesSquare },
  { to: routes.knowledge, label: '知识库', icon: BookOpen },
  { to: routes.resume, label: '简历', icon: FileText },
  { to: routes.stats, label: '统计', icon: TrendingUp },
]

async function onLogout(): Promise<void> {
  await logout()
  auth.clear()
  await router.push(routes.login)
}
</script>

<template>
  <div class="min-h-screen bg-[#F6FBF9]">
    <header
      v-if="auth.isLoggedIn"
      class="sticky top-0 z-10 px-6 py-2.5 flex items-center gap-6 relative"
    >
      <div class="absolute inset-x-0 top-0 h-14 bg-gradient-to-b from-teal-500/[0.08] to-transparent pointer-events-none" />
      <div class="flex items-center gap-2.5 relative">
        <div class="w-8 h-8 rounded-xl bg-teal-600 shadow-md shadow-teal-600/30 flex items-center justify-center">
          <Bot class="w-5 h-5 text-white" />
        </div>
        <span class="font-semibold text-teal-950">Smart Interviewer</span>
      </div>

      <nav class="flex items-center gap-1 text-sm relative">
        <router-link
          v-for="item in NAV_ITEMS"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl transition"
          :class="route.path.startsWith(item.to)
            ? 'bg-teal-600 text-white font-medium shadow-md shadow-teal-600/25'
            : 'text-teal-900/70 hover:text-teal-700 hover:bg-teal-100/60'"
        >
          <component :is="item.icon" class="w-4 h-4" />
          {{ item.label }}
        </router-link>
      </nav>

      <div class="ml-auto flex items-center gap-3 text-sm relative">
        <div class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-xl bg-teal-100 text-teal-800 flex items-center justify-center text-xs font-semibold">
            {{ auth.user?.username?.charAt(0).toUpperCase() ?? '?' }}
          </div>
          <span class="text-teal-950/80">{{ auth.user?.username }}</span>
        </div>
        <button
          class="flex items-center gap-1 text-teal-900/50 hover:text-red-500 transition"
          title="退出登录"
          @click="onLogout"
        >
          <LogOut class="w-4 h-4" />
        </button>
      </div>
    </header>

    <main class="max-w-5xl mx-auto px-4 py-8">
      <router-view />
    </main>
  </div>
</template>
