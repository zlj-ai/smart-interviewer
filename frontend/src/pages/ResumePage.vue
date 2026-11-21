<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { deleteResume, getResumeDetail, listResumes, uploadResume } from '@/api/resume'
import type { ResumeDetailResponse, ResumeResponse } from '@/types/api'

const resumes = ref<ResumeResponse[]>([])
const detail = ref<ResumeDetailResponse | null>(null)
const uploading = ref(false)
const error = ref('')

async function load(): Promise<void> {
  const page = await listResumes()
  resumes.value = page.content
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

function prettyAnalysis(): string {
  const raw = detail.value?.analysisJson
  if (!raw) {
    return ''
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <section class="bg-white rounded-lg border border-slate-200 p-6 space-y-3">
      <h2 class="font-semibold">简历</h2>
      <label class="inline-block border border-dashed rounded px-4 py-2 text-sm cursor-pointer hover:border-indigo-400">
        {{ uploading ? '上传中…' : '上传简历（pdf / docx / txt）' }}
        <input type="file" class="hidden" accept=".pdf,.docx,.txt" @change="onUpload" />
      </label>
      <p v-if="error" class="text-sm text-red-500">{{ error }}</p>
      <p class="text-xs text-slate-400">上传后异步完成 LLM 分析（状态 ANALYZED 后可查看）。</p>

      <table class="w-full text-sm">
        <thead>
          <tr class="text-left text-slate-500 border-b">
            <th class="py-2">文件</th><th>状态</th><th>attempt</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in resumes" :key="r.id" class="border-b">
            <td class="py-2 cursor-pointer hover:text-indigo-600" @click="select(r.id)">{{ r.filename }}</td>
            <td>{{ r.status }}</td>
            <td>{{ r.attempt }}</td>
            <td><button class="text-red-500" @click="remove(r.id)">删除</button></td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="detail" class="bg-white rounded-lg border border-slate-200 p-6">
      <h2 class="font-semibold mb-2">分析结果 · {{ detail.filename }}</h2>
      <pre v-if="detail.analysisJson" class="bg-slate-50 rounded p-3 text-xs whitespace-pre-wrap">{{ prettyAnalysis() }}</pre>
      <p v-else class="text-sm text-slate-400">暂无分析结果（状态：{{ detail.status }}）。</p>
    </section>
  </div>
</template>
