<script setup lang="ts">
import { ChevronRight, Plus } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createSession, listSessions } from '@/api/sessions'
import { listResumes } from '@/api/resume'
import { routes } from '@/constants/routes'
import StatusPill from '@/components/StatusPill.vue'

const router = useRouter()

const sessions = ref<Awaited<ReturnType<typeof listSessions>> | null>(null)
const direction = ref('JAVA_BACKEND')
const difficulty = ref('MEDIUM')
const resumeId = ref<number | null>(null)
const resumes = ref<{ id: number; filename: string; status: string }[]>([])
const creating = ref(false)
const createError = ref('')
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
  createError.value = ''
  try {
    const requestId = crypto.randomUUID()
    const session = await createSession(
      requestId,
      direction.value,
      difficulty.value,
      resumeId.value ?? undefined,
    )
    await router.push(routes.sessionDetail(session.id))
  } catch (e: unknown) {
    createError.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message
      ?? '创建失败，请稍后重试'
  } finally {
    creating.value = false
  }
}

async function loadResumes(): Promise<void> {
  const page = await listResumes()
  resumes.value = page.content.filter((r) => r.status === 'ANALYZED')
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

onMounted(() => {
  void load()
  void loadResumes()
})
</script>

<template>
  <div class="space-y-6">
    <section class="card p-6 space-y-4">
      <h2 class="font-semibold text-teal-950">发起新面试</h2>
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="block text-sm font-medium text-teal-900/70 mb-1">方向</label>
          <select v-model="direction" class="rounded-lg border border-teal-100 bg-white px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10">
            <option v-for="d in DIRECTIONS" :key="d.value" :value="d.value">{{ d.label }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-teal-900/70 mb-1">关联简历（可选，AI 将针对性出题）</label>
          <select v-model="resumeId" class="rounded-lg border border-teal-100 bg-white px-3 py-2 text-sm outline-none focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10">
            <option :value="null">不关联简历</option>
            <option v-for="r in resumes" :key="r.id" :value="r.id">{{ r.filename }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-teal-900/70 mb-1">难度</label>
          <select v-model="difficulty" class="input">
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </div>
        <button :disabled="creating" class="btn-primary flex items-center gap-1.5" @click="onCreate">
          <Plus class="w-4 h-4" />
          {{ creating ? '创建中…' : '创建面试' }}
        </button>
      </div>
      <p v-if="createError" class="rounded-lg bg-red-50 text-red-600 text-sm px-3 py-2 border border-red-100">{{ createError }}</p>
      <p class="text-xs text-teal-900/50">
        创建后由大模型异步生成题目，生成期间状态为「题目生成中」，完成后刷新即可看到题目。
      </p>
    </section>

    <section class="card p-6">
      <h2 class="font-semibold text-teal-950 mb-4">我的面试</h2>

      <div v-if="loading" class="text-sm text-teal-900/50 py-8 text-center">加载中…</div>

      <div v-else-if="(sessions?.content.length ?? 0) === 0"
           class="py-12 text-center border border-dashed border-teal-200 rounded-xl">
        <p class="text-sm text-teal-900/50">还没有面试记录，从上方创建第一场。</p>
      </div>

      <ul v-else class="divide-y divide-teal-100/70 -mx-2">
        <li v-for="s in sessions?.content" :key="s.id">
          <button
            class="w-full flex items-center gap-4 py-3 px-2 text-left rounded-xl hover:bg-teal-50 transition"
            @click="router.push(routes.sessionDetail(s.id))"
          >
            <span class="font-mono text-xs text-teal-900/40 w-10">#{{ s.id }}</span>
            <span class="font-medium text-teal-950 flex-1">{{ s.direction }}</span>
            <span class="text-sm text-teal-900/60">{{ s.difficulty }}</span>
            <StatusPill :status="s.status" />
            <span class="text-xs text-teal-900/40 w-40 text-right">{{ formatTime(s.createdAt) }}</span>
            <span class="text-teal-400">›</span>
          </button>
        </li>
      </ul>

      <div class="flex items-center gap-3 mt-4 text-sm">
        <button class="text-teal-700 disabled:text-teal-900/30" :disabled="page === 0" @click="goto(page - 1)">
          上一页
        </button>
        <span class="text-teal-900/50">第 {{ (sessions?.page ?? 0) + 1 }} / {{ totalPages }} 页</span>
        <button class="text-teal-700 disabled:text-teal-900/30" :disabled="page + 1 >= totalPages" @click="goto(page + 1)">
          下一页
        </button>
      </div>
    </section>
  </div>
</template>
