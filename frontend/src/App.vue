<script setup lang="ts">
import { BookOpen, Bot, FileText, LogOut, MessagesSquare } from 'lucide-vue-next'
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
]

async function onLogout(): Promise<void> {
  await logout()
  auth.clear()
  await router.push(routes.login)
}
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <header
      v-if="auth.isLoggedIn"
      class="sticky top-0 z-10 bg-white/80 backdrop-blur border-b border-slate-200 px-6 py-2.5 flex items-center gap-6"
    >
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-600 to-violet-600 flex items-center justify-center">
          <Bot class="w-5 h-5 text-white" />
        </div>
        <span class="font-semibold text-slate-800">Smart Interviewer</span>
      </div>

      <nav class="flex items-center gap-1 text-sm">
        <router-link
          v-for="item in NAV_ITEMS"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition"
          :class="route.path.startsWith(item.to)
            ? 'bg-indigo-50 text-indigo-700 font-medium'
            : 'text-slate-600 hover:text-indigo-600 hover:bg-slate-100'"
        >
          <component :is="item.icon" class="w-4 h-4" />
          {{ item.label }}
        </router-link>
      </nav>

      <div class="ml-auto flex items-center gap-3 text-sm">
        <div class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-xs font-semibold">
            {{ auth.user?.username?.charAt(0).toUpperCase() ?? '?' }}
          </div>
          <span class="text-slate-600">{{ auth.user?.username }}</span>
        </div>
        <button
          class="flex items-center gap-1 text-slate-400 hover:text-red-500 transition"
          title="退出登录"
          @click="onLogout"
        >
          <LogOut class="w-4 h-4" />
        </button>
      </div>
    </header>

    <main class="max-w-5xl mx-auto px-4 py-6">
      <router-view />
    </main>
  </div>
</template>
