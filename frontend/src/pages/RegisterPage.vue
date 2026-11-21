<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { register } from '@/api/auth'
import { routes } from '@/constants/routes'

const router = useRouter()

const username = ref('')
const password = ref('')
const email = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit(): Promise<void> {
  error.value = ''
  loading.value = true
  try {
    await register(username.value, password.value, email.value || undefined)
    await router.push(routes.login)
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message
      ?? '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center">
    <form class="w-96 bg-white rounded-lg border border-slate-200 p-8 space-y-4" @submit.prevent="onSubmit">
      <h1 class="text-xl font-semibold text-indigo-600">注册账号</h1>
      <div>
        <label class="block text-sm text-slate-500 mb-1">用户名（3-32 位字母数字）</label>
        <input v-model="username" class="w-full border rounded px-3 py-2" required />
      </div>
      <div>
        <label class="block text-sm text-slate-500 mb-1">密码（至少 8 位）</label>
        <input v-model="password" type="password" class="w-full border rounded px-3 py-2" required />
      </div>
      <div>
        <label class="block text-sm text-slate-500 mb-1">邮箱（可选）</label>
        <input v-model="email" type="email" class="w-full border rounded px-3 py-2" />
      </div>
      <p v-if="error" class="text-sm text-red-500">{{ error }}</p>
      <button :disabled="loading" class="w-full bg-indigo-600 text-white rounded py-2 disabled:opacity-50">
        {{ loading ? '注册中…' : '注册' }}
      </button>
      <p class="text-sm text-slate-500">
        已有账号？
        <router-link :to="routes.login" class="text-indigo-600">去登录</router-link>
      </p>
    </form>
  </div>
</template>
