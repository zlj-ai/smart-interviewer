<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createSession, listSessions } from '@/api/sessions'
import { routes } from '@/constants/routes'

const router = useRouter()

const sessions = ref<Awaited<ReturnType<typeof listSessions>> | null>(null)
const direction = ref('JAVA_BACKEND')
const difficulty = ref('MEDIUM')
const creating = ref(false)
const page = ref(0)
const totalPages = ref(1)

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
  sessions.value = await listSessions(page.value)
  totalPages.value = sessions.value.totalPages
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

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-slate-200 p-6 space-y-3">
      <h2 class="font-semibold">发起新面试</h2>
      <div class="flex items-end gap-3">
        <div>
          <label class="block text-sm text-slate-500 mb-1">方向</label>
          <select v-model="direction" class="border rounded px-3 py-2">
            <option v-for="d in DIRECTIONS" :key="d.value" :value="d.value">{{ d.label }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm text-slate-500 mb-1">难度</label>
          <select v-model="difficulty" class="border rounded px-3 py-2">
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </div>
        <button :disabled="creating" class="bg-indigo-600 text-white rounded px-4 py-2 disabled:opacity-50"
                @click="onCreate">
          {{ creating ? '创建中…' : '创建面试' }}
        </button>
      </div>
      <p class="text-xs text-slate-400">创建后系统会异步生成题目（由大模型完成），稍候刷新即可看到题目。</p>
    </section>

    <section class="bg-white rounded-lg border border-slate-200 p-6">
      <h2 class="font-semibold mb-3">我的面试</h2>
      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-slate-500 border-b">
            <th class="py-2">ID</th>
            <th>方向</th>
            <th>难度</th>
            <th>状态</th>
            <th>创建时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in sessions?.content ?? []" :key="s.id" class="border-b hover:bg-slate-50 cursor-pointer"
              @click="router.push(routes.sessionDetail(s.id))">
            <td class="py-2">{{ s.id }}</td>
            <td>{{ s.direction }}</td>
            <td>{{ s.difficulty }}</td>
            <td>{{ s.status }}</td>
            <td>{{ new Date(s.createdAt).toLocaleString() }}</td>
          </tr>
        </tbody>
      </table>
      <div class="flex gap-2 mt-3">
        <button class="text-sm text-indigo-600 disabled:text-slate-300" :disabled="page === 0" @click="goto(page - 1)">
          上一页
        </button>
        <span class="text-sm text-slate-400">第 {{ (sessions?.page ?? 0) + 1 }} / {{ totalPages }} 页</span>
        <button class="text-sm text-indigo-600 disabled:text-slate-300"
                :disabled="page + 1 >= totalPages" @click="goto(page + 1)">
          下一页
        </button>
      </div>
    </section>
  </div>
</template>
