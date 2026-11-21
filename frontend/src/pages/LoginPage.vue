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
        <h2 class="text-2xl font-bold text-teal-950">欢迎回来</h2>

        <div class="space-y-2">
          <label class="block text-sm font-medium text-teal-900/70">用户名</label>
          <input
            v-model="username"
            required
            autocomplete="username"
            class="input w-full"
            placeholder="输入用户名"
          />
        </div>
        <div class="space-y-2">
          <label class="block text-sm font-medium text-teal-900/70">密码</label>
          <input
            v-model="password"
            type="password"
            required
            autocomplete="current-password"
            class="input w-full"
            placeholder="输入密码"
          />
        </div>

        <p v-if="error" class="rounded-xl bg-red-50 text-red-600 text-sm px-3 py-2 border border-red-100">
          {{ error }}
        </p>

        <button :disabled="loading" class="btn-primary w-full">
          {{ loading ? '登录中…' : '登录' }}
        </button>

        <p class="text-sm text-teal-900/60">
          没有账号？
          <router-link :to="routes.register" class="text-teal-700 font-medium hover:underline">去注册</router-link>
        </p>
      </form>
    </div>
  </div>
</template>
