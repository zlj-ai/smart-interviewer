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
    <section class="card p-6 space-y-4">
      <h2 class="font-semibold text-teal-950">简历</h2>
      <label
        class="inline-flex items-center gap-2 border border-dashed border-teal-200 rounded-xl px-5 py-3 text-sm text-teal-900/60 cursor-pointer transition hover:border-teal-500 hover:text-teal-700 hover:bg-teal-50/60"
      >
        {{ uploading ? '上传中…' : '上传简历（pdf / docx / txt）' }}
        <input type="file" class="hidden" accept=".pdf,.docx,.txt" @change="onUpload" />
      </label>
      <p v-if="error" class="rounded-xl bg-red-50 text-red-600 text-sm px-3 py-2 border border-red-100">{{ error }}</p>
      <p class="text-xs text-teal-900/50">上传后由大模型异步完成分析，状态变为「已分析」后点击文件名查看结果。</p>

      <div v-if="loading" class="text-sm text-teal-900/50 py-2 text-center">加载中…</div>
      <ul v-else-if="resumes.length > 0" class="divide-y divide-teal-100/70">
        <li v-for="r in resumes" :key="r.id" class="flex items-center gap-4 py-2.5">
          <button class="text-sm text-teal-950 hover:text-teal-700 flex-1 text-left" @click="select(r.id)">
            {{ r.filename }}
          </button>
          <span class="text-xs text-teal-900/40">{{ r.charCount }} 字符</span>
          <StatusPill :status="r.status" />
          <button class="text-teal-900/30 hover:text-red-500 transition" title="删除" @click="remove(r.id)">
            <Trash2 class="w-4 h-4" />
          </button>
        </li>
      </ul>
      <p v-else class="text-sm text-teal-900/50">还没有简历。</p>
    </section>

    <section v-if="detail" class="card p-6 space-y-5">
      <div class="flex items-center justify-between">
        <h2 class="font-semibold text-teal-950">分析结果 · {{ detail.filename }}</h2>
        <StatusPill :status="detail.status" />
      </div>

      <template v-if="analysis">
        <p class="text-sm text-teal-950/80 leading-relaxed">{{ analysis.basicInfo }}</p>

        <div>
          <h3 class="text-sm font-medium text-teal-900/70 mb-2">技能清单</h3>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="s in analysis.skills"
              :key="s"
              class="text-xs bg-teal-100/80 text-teal-800 rounded-full px-2.5 py-1"
            >
              {{ s }}
            </span>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-medium text-teal-900/70 mb-2">经历摘要</h3>
          <ul class="list-disc list-inside text-sm text-teal-950/80 space-y-1 marker:text-teal-500">
            <li v-for="e in analysis.experiences" :key="e">{{ e }}</li>
          </ul>
        </div>

        <div class="grid md:grid-cols-2 gap-4">
          <div class="rounded-xl bg-emerald-50 border border-emerald-100 p-4">
            <h3 class="text-sm font-medium text-emerald-700 flex items-center gap-1.5 mb-1.5">
              <Sparkles class="w-4 h-4" /> 亮点
            </h3>
            <p class="text-sm text-emerald-900/80">{{ analysis.strengths }}</p>
          </div>
          <div class="rounded-xl bg-amber-50 border border-amber-100 p-4">
            <h3 class="text-sm font-medium text-amber-700 flex items-center gap-1.5 mb-1.5">
              <Lightbulb class="w-4 h-4" /> 短板与风险
            </h3>
            <p class="text-sm text-amber-900/80">{{ analysis.weaknesses }}</p>
          </div>
        </div>

        <div>
          <h3 class="text-sm font-medium text-teal-900/70 mb-2">面试准备建议</h3>
          <ul class="list-disc list-inside text-sm text-teal-950/80 space-y-1 marker:text-teal-500">
            <li v-for="s in analysis.suggestions" :key="s">{{ s }}</li>
          </ul>
        </div>
      </template>
      <p v-else class="text-sm text-teal-900/50">
        暂无分析结果（状态：{{ detail.status }}）。状态为 FAILED 可重新上传。
      </p>
    </section>
  </div>
</template>
