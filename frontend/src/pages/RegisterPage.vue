<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import AuthShell from '@/components/AuthShell.vue'
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
  <div class="min-h-screen grid lg:grid-cols-2">
    <AuthShell
      title="创建账号"
      subtitle="注册后即可创建面试、上传简历并搭建自己的知识库。"
    />

    <div class="flex items-center justify-center p-8">
      <form class="w-full max-w-sm space-y-5" @submit.prevent="onSubmit">
        <h2 class="text-2xl font-bold text-teal-950">注册账号</h2>

        <div class="space-y-2">
          <label class="block text-sm font-medium text-teal-900/70">用户名（3-32 位字母数字）</label>
          <input v-model="username" required class="input w-full" placeholder="输入用户名" />
        </div>
        <div class="space-y-2">
          <label class="block text-sm font-medium text-teal-900/70">密码（至少 8 位）</label>
          <input v-model="password" type="password" required class="input w-full" placeholder="输入密码" />
        </div>
        <div class="space-y-2">
          <label class="block text-sm font-medium text-teal-900/70">邮箱（可选）</label>
          <input v-model="email" type="email" class="input w-full" placeholder="name@example.com" />
        </div>

        <p v-if="error" class="rounded-xl bg-red-50 text-red-600 text-sm px-3 py-2 border border-red-100">
          {{ error }}
        </p>

        <button :disabled="loading" class="btn-primary w-full">
          {{ loading ? '注册中…' : '注册' }}
        </button>

        <p class="text-sm text-teal-900/60">
          已有账号？
          <router-link :to="routes.login" class="text-teal-700 font-medium hover:underline">去登录</router-link>
        </p>
      </form>
    </div>
  </div>
</template>
