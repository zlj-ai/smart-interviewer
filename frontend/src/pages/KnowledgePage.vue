<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { createKb, listDocuments, listKbs, uploadDocument } from '@/api/knowledge'
import StatusPill from '@/components/StatusPill.vue'
import type { KbDocumentResponse, KbResponse } from '@/types/api'

const kbs = ref<KbResponse[]>([])
const selectedKb = ref<number | null>(null)
const documents = ref<KbDocumentResponse[]>([])
const name = ref('')
const description = ref('')
const uploading = ref(false)
const error = ref('')
const loading = ref(true)

const selectedKbName = computed(
  () => kbs.value.find((kb) => kb.id === selectedKb.value)?.name ?? '',
)

async function loadKbs(): Promise<void> {
  loading.value = true
  try {
    kbs.value = await listKbs()
    if (selectedKb.value === null && kbs.value.length > 0) {
      await select(kbs.value[0].id)
    }
  } finally {
    loading.value = false
  }
}

async function select(kbId: number): Promise<void> {
  selectedKb.value = kbId
  documents.value = await listDocuments(kbId)
}

async function onCreate(): Promise<void> {
  error.value = ''
  try {
    const kb = await createKb(name.value, description.value)
    name.value = ''
    description.value = ''
    await loadKbs()
    await select(kb.id)
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? '创建失败'
  }
}

async function onUpload(event: Event): Promise<void> {
  if (selectedKb.value === null) {
    return
  }
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploading.value = true
  error.value = ''
  try {
    await uploadDocument(selectedKb.value, file)
    documents.value = await listDocuments(selectedKb.value)
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? '上传失败'
  } finally {
    uploading.value = false
    input.value = ''
  }
}

onMounted(loadKbs)
</script>

<template>
  <div class="space-y-6">
    <section class="bg-white rounded-xl border border-slate-200 p-6 space-y-4">
      <h2 class="font-semibold text-slate-800">创建知识库</h2>
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="block text-sm font-medium text-slate-600 mb-1">名称</label>
          <input v-model="name" class="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-100" placeholder="如：Java 面试笔记" />
        </div>
        <div class="flex-1 min-w-48">
          <label class="block text-sm font-medium text-slate-600 mb-1">描述（可选）</label>
          <input v-model="description" class="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400 focus:ring-4 focus:ring-indigo-100" placeholder="这个知识库放什么内容" />
        </div>
        <button class="rounded-lg bg-gradient-to-r from-indigo-600 to-violet-600 text-white px-5 py-2 text-sm font-medium transition hover:opacity-95" @click="onCreate">
          创建
        </button>
      </div>
      <p v-if="error" class="rounded-lg bg-rose-50 text-rose-600 text-sm px-3 py-2">{{ error }}</p>
    </section>

    <section class="bg-white rounded-xl border border-slate-200 p-6 space-y-5">
      <div v-if="loading" class="text-sm text-slate-400 py-4 text-center">加载中…</div>
      <template v-else>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="kb in kbs"
            :key="kb.id"
            class="px-4 py-1.5 rounded-full border text-sm transition"
            :class="selectedKb === kb.id
              ? 'bg-indigo-600 text-white border-indigo-600'
              : 'text-slate-600 hover:border-indigo-400 hover:text-indigo-600'"
            @click="select(kb.id)"
          >
            {{ kb.name }}
          </button>
          <span v-if="kbs.length === 0" class="text-sm text-slate-400 py-1">还没有知识库，先在上方创建一个。</span>
        </div>

        <div v-if="selectedKb !== null" class="space-y-4">
          <h3 class="text-sm font-medium text-slate-700">
            「{{ selectedKbName }}」的文档
          </h3>

          <label
            class="inline-flex items-center gap-2 border border-dashed border-slate-300 rounded-lg px-5 py-3 text-sm text-slate-500 cursor-pointer transition hover:border-indigo-400 hover:text-indigo-600"
          >
            {{ uploading ? '上传解析中…' : '上传文档（pdf / docx / txt / md）' }}
            <input type="file" class="hidden" accept=".pdf,.docx,.txt,.md" @change="onUpload" />
          </label>

          <p v-if="documents.length === 0" class="text-sm text-slate-400">还没有文档。</p>

          <ul v-else class="divide-y divide-slate-100">
            <li v-for="d in documents" :key="d.id" class="flex items-center gap-4 py-2.5">
              <span class="text-sm text-slate-800 flex-1">{{ d.filename }}</span>
              <span class="text-xs text-slate-400">{{ d.charCount }} 字符</span>
              <span class="text-xs text-slate-400">{{ d.chunkCount }} 块</span>
              <StatusPill :status="d.status" />
            </li>
          </ul>

          <p class="text-xs text-slate-400">
            状态说明：待处理 → 向量化中 → 已向量化（可被 RAG 检索）；失败可重新上传同名文件。
          </p>
        </div>
      </template>
    </section>
  </div>
</template>
