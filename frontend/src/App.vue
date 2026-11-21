<script setup lang="ts">
import { BookOpen, FileText, LogOut, MessagesSquare } from 'lucide-vue-next'
import { useRouter } from 'vue-router'

import { logout } from '@/api/auth'
import { routes } from '@/constants/routes'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function onLogout(): Promise<void> {
  await logout()
  auth.clear()
  await router.push(routes.login)
}
</script>

<template>
  <div class="min-h-screen">
    <header v-if="auth.isLoggedIn" class="bg-white border-b border-slate-200 px-6 py-3 flex items-center gap-6">
      <span class="font-semibold text-indigo-600">Smart Interviewer</span>
      <nav class="flex items-center gap-4 text-sm">
        <router-link :to="routes.sessions" class="flex items-center gap-1 hover:text-indigo-600">
          <MessagesSquare class="w-4 h-4" /> 面试
        </router-link>
        <router-link :to="routes.knowledge" class="flex items-center gap-1 hover:text-indigo-600">
          <BookOpen class="w-4 h-4" /> 知识库
        </router-link>
        <router-link :to="routes.resume" class="flex items-center gap-1 hover:text-indigo-600">
          <FileText class="w-4 h-4" /> 简历
        </router-link>
      </nav>
      <div class="ml-auto flex items-center gap-3 text-sm">
        <span class="text-slate-500">{{ auth.user?.username }}</span>
        <button class="flex items-center gap-1 text-slate-500 hover:text-red-500" @click="onLogout">
          <LogOut class="w-4 h-4" /> 退出
        </button>
      </div>
    </header>
    <main class="max-w-5xl mx-auto px-4 py-6">
      <router-view />
    </main>
  </div>
</template>
