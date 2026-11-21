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
    <section class="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
      <h2 class="font-semibold text-slate-800">简历</h2>
      <label
        class="inline-flex items-center gap-2 border border-dashed border-slate-300 rounded-lg px-5 py-3 text-sm text-slate-500 cursor-pointer transition hover:border-indigo-400 hover:text-indigo-600"
      >
        {{ uploading ? '上传中…' : '上传简历（pdf / docx / txt）' }}
        <input type="file" class="hidden" accept=".pdf,.docx,.txt" @change="onUpload" />
      </label>
      <p v-if="error" class="rounded-lg bg-rose-50 text-rose-600 text-sm px-3 py-2">{{ error }}</p>
      <p class="text-xs text-slate-400">上传后由大模型异步完成分析，状态变为「已分析」后点击文件名查看结果。</p>

      <div v-if="loading" class="text-sm text-slate-400 py-2 text-center">加载中…</div>
      <ul v-else-if="resumes.length > 0" class="divide-y divide-slate-100">
        <li v-for="r in resumes" :key="r.id" class="flex items-center gap-4 py-2.5">
          <button class="text-sm text-slate-800 hover:text-indigo-600 flex-1 text-left" @click="select(r.id)">
            {{ r.filename }}
          </button>
          <span class="text-xs text-slate-400">{{ r.charCount }} 字符</span>
          <StatusPill :status="r.status" />
          <button class="text-slate-300 hover:text-rose-500 transition" title="删除" @click="remove(r.id)">
            <Trash2 class="w-4 h-4" />
          </button>
        </li>
      </ul>
      <p v-else class="text-sm text-slate-400">还没有简历。</p>
    </section>

    <section v-if="detail" class="bg-white rounded-xl border border-slate-200 p-6 space-y-5">
      <div class="flex items-center justify-between">
        <h2 class="font-semibold text-slate-800">分析结果 · {{ detail.filename }}</h2>
        <StatusPill :status="detail.status" />
      </div>

      <template v-if="analysis">
        <p class="text-sm text-slate-600">{{ analysis.basicInfo }}</p>

        <div>
          <h3 class="text-sm font-medium text-slate-700 mb-2">技能清单</h3>
          <div class="flex flex-wrap gap-2">
            <span v-for="s in analysis.skills" :key="s"
                  class="text-xs bg-indigo-50 text-indigo-700 rounded-full px-2.5 py-1">
              {{ s }}
            </span>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-medium text-slate-700 mb-2">经历摘要</h3>
          <ul class="list-disc list-inside text-sm text-slate-600 space-y-1">
            <li v-for="e in analysis.experiences" :key="e">{{ e }}</li>
          </ul>
        </div>

        <div class="grid md:grid-cols-2 gap-4">
          <div class="rounded-lg bg-emerald-50 p-4">
            <h3 class="text-sm font-medium text-emerald-700 flex items-center gap-1.5 mb-1">
              <Sparkles class="w-4 h-4" /> 亮点
            </h3>
            <p class="text-sm text-emerald-800/80">{{ analysis.strengths }}</p>
          </div>
          <div class="rounded-lg bg-amber-50 p-4">
            <h3 class="text-sm font-medium text-amber-700 flex items-center gap-1.5 mb-1">
              <Lightbulb class="w-4 h-4" /> 短板与风险
            </h3>
            <p class="text-sm text-amber-800/80">{{ analysis.weaknesses }}</p>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-medium text-slate-700 mb-2">面试准备建议</h3>
          <ul class="list-disc list-inside text-sm text-slate-600 space-y-1">
            <li v-for="s in analysis.suggestions" :key="s">{{ s }}</li>
          </ul>
        </div>
      </template>
      <p v-else class="text-sm text-slate-400">
        暂无分析结果（状态：{{ detail.status }}）。状态为 FAILED 可重新上传。
      </p>
    </section>
  </div>
</template>
