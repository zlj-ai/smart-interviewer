<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Lightbulb, Sparkles, Trash2 } from 'lucide-vue-next'

import { deleteResume, getResumeDetail, listResumes, uploadResume } from '@/api/resume'
import StatusPill from '@/components/StatusPill.vue'
import type { ResumeDetailResponse, ResumeResponse } from '@/types/api'

const resumes = ref<ResumeResponse[]>([])
const detail = ref<ResumeDetailResponse | null>(null)
const uploading = ref(false)
const error = ref('')
const loading = ref(true)

interface Analysis {
  basicInfo: string
  skills: string[]
  experiences: string[]
  strengths: string
  weaknesses: string
  suggestions: string[]
}

const analysis = computed<Analysis | null>(() => {
  const raw = detail.value?.analysisJson
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as Analysis
  } catch {
    return null
  }
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listResumes()
    resumes.value = page.content
  } finally {
    loading.value = false
  }
}

async function onUpload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploading.value = true
  error.value = ''
  try {
    await uploadResume(file)
    await load()
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? '上传失败'
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function select(id: number): Promise<void> {
  detail.value = await getResumeDetail(id)
}

async function remove(id: number): Promise<void> {
  await deleteResume(id)
  if (detail.value?.id === id) {
    detail.value = null
  }
  await load()
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <section class="rounded-2xl bg-zinc-900/70 border border-white/[0.06] p-6 space-y-4">
      <h2 class="font-semibold text-zinc-100">简历</h2>
      <label
        class="inline-flex items-center gap-2 border border-dashed border-white/15 rounded-lg px-5 py-3 text-sm text-zinc-400 cursor-pointer transition hover:border-violet-500/50 hover:text-violet-300"
      >
        {{ uploading ? '上传中…' : '上传简历（pdf / docx / txt）' }}
        <input type="file" class="hidden" accept=".pdf,.docx,.txt" @change="onUpload" />
      </label>
      <p v-if="error" class="rounded-lg bg-rose-500/10 text-rose-400 text-sm px-3 py-2 ring-1 ring-rose-500/30">{{ error }}</p>
      <p class="text-xs text-zinc-600">上传后由大模型异步完成分析，状态变为「已分析」后点击文件名查看结果。</p>

      <div v-if="loading" class="text-sm text-zinc-500 py-2 text-center">加载中…</div>
      <ul v-else-if="resumes.length > 0" class="divide-y divide-white/[0.06]">
        <li v-for="r in resumes" :key="r.id" class="flex items-center gap-4 py-2.5">
          <button class="text-sm text-zinc-200 hover:text-violet-300 flex-1 text-left" @click="select(r.id)">
            {{ r.filename }}
          </button>
          <span class="text-xs text-zinc-600">{{ r.charCount }} 字符</span>
          <StatusPill :status="r.status" />
          <button class="text-zinc-600 hover:text-rose-400 transition" title="删除" @click="remove(r.id)">
            <Trash2 class="w-4 h-4" />
          </button>
        </li>
      </ul>
      <p v-else class="text-sm text-zinc-500">还没有简历。</p>
    </section>

    <section v-if="detail" class="rounded-2xl bg-zinc-900/70 border border-white/[0.06] p-6 space-y-5">
      <div class="flex items-center justify-between">
        <h2 class="font-semibold text-zinc-100">分析结果 · {{ detail.filename }}</h2>
        <StatusPill :status="detail.status" />
      </div>

      <template v-if="analysis">
        <p class="text-sm text-zinc-300 leading-relaxed">{{ analysis.basicInfo }}</p>

        <div>
          <h3 class="text-sm font-medium text-zinc-400 mb-2">技能清单</h3>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="s in analysis.skills"
              :key="s"
              class="text-xs bg-violet-500/10 text-violet-300 ring-1 ring-violet-500/30 rounded-full px-2.5 py-1"
            >
              {{ s }}
            </span>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-medium text-zinc-400 mb-2">经历摘要</h3>
          <ul class="list-disc list-inside text-sm text-zinc-300 space-y-1 marker:text-violet-500">
            <li v-for="e in analysis.experiences" :key="e">{{ e }}</li>
          </ul>
        </div>

        <div class="grid md:grid-cols-2 gap-4">
          <div class="rounded-lg bg-emerald-500/[0.07] border border-emerald-500/20 p-4">
            <h3 class="text-sm font-medium text-emerald-300 flex items-center gap-1.5 mb-1.5">
              <Sparkles class="w-4 h-4" /> 亮点
            </h3>
            <p class="text-sm text-emerald-100/80">{{ analysis.strengths }}</p>
          </div>
          <div class="rounded-lg bg-amber-500/[0.07] border border-amber-500/20 p-4">
            <h3 class="text-sm font-medium text-amber-300 flex items-center gap-1.5 mb-1.5">
              <Lightbulb class="w-4 h-4" /> 短板与风险
            </h3>
            <p class="text-sm text-amber-100/80">{{ analysis.weaknesses }}</p>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-medium text-zinc-400 mb-2">面试准备建议</h3>
          <ul class="list-disc list-inside text-sm text-zinc-300 space-y-1 marker:text-violet-500">
            <li v-for="s in analysis.suggestions" :key="s">{{ s }}</li>
          </ul>
        </div>
      </template>
      <p v-else class="text-sm text-zinc-500">
        暂无分析结果（状态：{{ detail.status }}）。状态为 FAILED 可重新上传。
      </p>
    </section>
  </div>
</template>
