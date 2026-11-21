<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createSession, listSessions } from '@/api/sessions'
import { routes } from '@/constants/routes'
import StatusPill from '@/components/StatusPill.vue'

const router = useRouter()

const sessions = ref<Awaited<ReturnType<typeof listSessions>> | null>(null)
const direction = ref('JAVA_BACKEND')
const difficulty = ref('MEDIUM')
const creating = ref(false)
const page = ref(0)
const totalPages = ref(1)
const loading = ref(true)

const DIRECTIONS = [
  { value: 'JAVA_BACKEND', label: 'Java 后端' },
  { value: 'FRONTEND', label: '前端' },
  { value: 'ALGORITHM', label: '算法' },
  { value: 'SYSTEM_DESIGN', label: '系统设计' },
  { value: 'DATABASE', label: '数据库' },
  { value: 'DISTRIBUTED_SYSTEMS', label: '分布式系统' },
  { value: 'PROJECT_DEEP_DIVE', label: '项目深挖' },
  { value: 'BEHAVIORAL', label: '行为面试' },
]

async function load(): Promise<void> {
  loading.value = true
  try {
    sessions.value = await listSessions(page.value)
    totalPages.value = sessions.value.totalPages
  } finally {
    loading.value = false
  }
}

async function onCreate(): Promise<void> {
  creating.value = true
  try {
    const requestId = crypto.randomUUID()
    const session = await createSession(requestId, direction.value, difficulty.value)
    await router.push(routes.sessionDetail(session.id))
  } finally {
    creating.value = false
  }
}

async function goto(pageNo: number): Promise<void> {
  if (pageNo < 0 || pageNo >= totalPages.value) {
    return
  }
  page.value = pageNo
  await load()
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <section class="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
      <h2 class="font-semibold text-slate-800">发起新面试</h2>
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="block text-sm font-medium text-slate-600 mb-1">方向</label>
          <select v-model="direction" class="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-100">
            <option v-for="d in DIRECTIONS" :key="d.value" :value="d.value">{{ d.label }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-slate-600 mb-1">难度</label>
          <select v-model="difficulty" class="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-100">
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </div>
        <button
          :disabled="creating"
          class="rounded-lg bg-gradient-to-r from-indigo-600 to-violet-600 text-white px-5 py-2 text-sm font-medium transition hover:opacity-95 disabled:opacity-50"
          @click="onCreate"
        >
          {{ creating ? '创建中…' : '创建面试' }}
        </button>
      </div>
      <p class="text-xs text-slate-400">
        创建后由大模型异步生成题目，生成期间状态为「题目生成中」，完成后刷新即可看到题目。
      </p>
    </section>

    <section class="bg-white rounded-xl border border-slate-200 p-6">
      <h2 class="font-semibold text-slate-800 mb-4">我的面试</h2>

      <div v-if="loading" class="text-sm text-slate-400 py-6 text-center">加载中…</div>

      <div v-else-if="(sessions?.content.length ?? 0) === 0" class="py-10 text-center">
        <p class="text-sm text-slate-400">还没有面试记录，从上方创建第一场。</p>
      </div>

      <ul v-else class="divide-y divide-slate-100">
        <li v-for="s in sessions?.content" :key="s.id">
          <button
            class="w-full flex items-center gap-4 py-3 text-left hover:bg-slate-50 rounded-lg px-2 -mx-2 transition"
            @click="router.push(routes.sessionDetail(s.id))"
          >
            <span class="font-mono text-xs text-slate-400 w-10">#{{ s.id }}</span>
            <span class="font-medium text-slate-800 flex-1">{{ s.direction }}</span>
            <span class="text-sm text-slate-500">{{ s.difficulty }}</span>
            <StatusPill :status="s.status" />
            <span class="text-xs text-slate-400 w-40 text-right">{{ formatTime(s.createdAt) }}</span>
          </button>
        </li>
      </ul>

      <div class="flex items-center gap-3 mt-4 text-sm">
        <button class="text-indigo-600 disabled:text-slate-300" :disabled="page === 0" @click="goto(page - 1)">
          上一页
        </button>
        <span class="text-slate-400">第 {{ (sessions?.page ?? 0) + 1 }} / {{ totalPages }} 页</span>
        <button class="text-indigo-600 disabled:text-slate-300" :disabled="page + 1 >= totalPages" @click="goto(page + 1)">
          下一页
        </button>
      </div>
    </section>
  </div>
</template>
