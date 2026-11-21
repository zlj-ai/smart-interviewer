<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

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
  <div class="min-h-screen flex items-center justify-center">
    <form class="w-96 bg-white rounded-lg border border-slate-200 p-8 space-y-4" @submit.prevent="onSubmit">
      <h1 class="text-xl font-semibold text-indigo-600">Smart Interviewer</h1>
      <div>
        <label class="block text-sm text-slate-500 mb-1">用户名</label>
        <input v-model="username" class="w-full border rounded px-3 py-2" required />
      </div>
      <div>
        <label class="block text-sm text-slate-500 mb-1">密码</label>
        <input v-model="password" type="password" class="w-full border rounded px-3 py-2" required />
      </div>
      <p v-if="error" class="text-sm text-red-500">{{ error }}</p>
      <button :disabled="loading" class="w-full bg-indigo-600 text-white rounded py-2 disabled:opacity-50">
        {{ loading ? '登录中…' : '登录' }}
      </button>
      <p class="text-sm text-slate-500">
        没有账号？
        <router-link :to="routes.register" class="text-indigo-600">去注册</router-link>
      </p>
    </form>
  </div>
</template>
