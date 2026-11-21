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
  <div class="min-h-screen relative">
    <!-- 顶部氛围光 -->
    <div class="pointer-events-none fixed inset-x-0 top-0 h-72 bg-gradient-to-b from-violet-600/15 to-transparent" />

    <header
      v-if="auth.isLoggedIn"
      class="sticky top-0 z-10 backdrop-blur-xl bg-zinc-950/70 border-b border-white/[0.06] px-6 py-2.5 flex items-center gap-6 relative"
    >
      <div class="flex items-center gap-2.5">
        <div class="w-8 h-8 rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-600 shadow-lg shadow-violet-600/40 flex items-center justify-center">
          <Bot class="w-5 h-5 text-white" />
        </div>
        <span class="font-semibold tracking-wide">
          <span class="bg-gradient-to-r from-violet-300 to-fuchsia-300 bg-clip-text text-transparent">Smart</span>
          <span class="text-zinc-100"> Interviewer</span>
        </span>
      </div>

      <nav class="flex items-center gap-1 text-sm">
        <router-link
          v-for="item in NAV_ITEMS"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition"
          :class="route.path.startsWith(item.to)
            ? 'bg-violet-500/15 text-violet-300 ring-1 ring-violet-500/30 font-medium'
            : 'text-zinc-400 hover:text-zinc-100 hover:bg-white/5'"
        >
          <component :is="item.icon" class="w-4 h-4" />
          {{ item.label }}
        </router-link>
      </nav>

      <div class="ml-auto flex items-center gap-3 text-sm">
        <div class="flex items-center gap-2">
          <div class="w-7 h-7 rounded-full bg-gradient-to-br from-violet-500/40 to-fuchsia-500/40 ring-1 ring-white/20 flex items-center justify-center text-xs font-semibold text-violet-200">
            {{ auth.user?.username?.charAt(0).toUpperCase() ?? '?' }}
          </div>
          <span class="text-zinc-300">{{ auth.user?.username }}</span>
        </div>
        <button
          class="flex items-center gap-1 text-zinc-500 hover:text-rose-400 transition"
          title="退出登录"
          @click="onLogout"
        >
          <LogOut class="w-4 h-4" />
        </button>
      </div>
    </header>

    <main class="max-w-5xl mx-auto px-4 py-8 relative">
      <router-view />
    </main>
  </div>
</template>
