<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import AuthShell from '@/components/AuthShell.vue'
import { login } from '@/api/auth'
import { routes } from '@/constants/routes'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    const data = await login(username.value, password.value)
    auth.setUser(data.user)
    await router.push(routes.sessions)
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message
      ?? '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen grid lg:grid-cols-2">
    <AuthShell
      title="Smart Interviewer"
      subtitle="把简历、知识库和真实追问流程搬进一次练习，答案有评分，弱点有建议。"
    />

    <div class="flex items-center justify-center p-8">
      <form class="w-full max-w-sm space-y-5" @submit.prevent="onSubmit">
        <h2 class="text-2xl font-bold text-zinc-100">欢迎回来</h2>

        <div class="space-y-2">
          <label class="block text-sm font-medium text-zinc-400">用户名</label>
          <input
            v-model="username"
            required
            autocomplete="username"
            class="w-full rounded-lg bg-zinc-900 border border-white/10 px-3.5 py-2.5 text-sm text-zinc-100 placeholder-zinc-500 outline-none transition focus:border-violet-500/60 focus:ring-4 focus:ring-violet-500/10"
            placeholder="输入用户名"
          />
        </div>
        <div class="space-y-2">
          <label class="block text-sm font-medium text-zinc-400">密码</label>
          <input
            v-model="password"
            type="password"
            required
            autocomplete="current-password"
            class="w-full rounded-lg bg-zinc-900 border border-white/10 px-3.5 py-2.5 text-sm text-zinc-100 placeholder-zinc-500 outline-none transition focus:border-violet-500/60 focus:ring-4 focus:ring-violet-500/10"
            placeholder="输入密码"
          />
        </div>

        <p v-if="error" class="rounded-lg bg-rose-500/10 text-rose-400 text-sm px-3 py-2 ring-1 ring-rose-500/30">
          {{ error }}
        </p>

        <button
          :disabled="loading"
          class="w-full rounded-lg bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white py-2.5 text-sm font-medium shadow-lg shadow-violet-600/30 transition hover:brightness-110 disabled:opacity-50"
        >
          {{ loading ? '登录中…' : '登录' }}
        </button>

        <p class="text-sm text-zinc-500">
          没有账号？
          <router-link :to="routes.register" class="text-violet-400 font-medium hover:text-violet-300">去注册</router-link>
        </p>
      </form>
    </div>
  </div>
</template>
