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

const inputClass =
  'rounded-lg bg-zinc-900 border border-white/10 px-3 py-2 text-sm text-zinc-100 placeholder-zinc-500 outline-none transition focus:border-violet-500/60 focus:ring-4 focus:ring-violet-500/10'

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
    <section class="rounded-2xl bg-zinc-900/70 border border-white/[0.06] p-6 space-y-4">
      <h2 class="font-semibold text-zinc-100">创建知识库</h2>
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="block text-sm font-medium text-zinc-400 mb-1">名称</label>
          <input v-model="name" :class="inputClass" placeholder="如：Java 面试笔记" />
        </div>
        <div class="flex-1 min-w-48">
          <label class="block text-sm font-medium text-zinc-400 mb-1">描述（可选）</label>
          <input v-model="description" :class="inputClass" placeholder="这个知识库放什么内容" />
        </div>
        <button
          class="rounded-lg bg-gradient-to-r from-violet-600 to-fuchsia-600 text-white px-5 py-2 text-sm font-medium shadow-lg shadow-violet-600/30 transition hover:brightness-110"
          @click="onCreate"
        >
          创建
        </button>
      </div>
      <p v-if="error" class="rounded-lg bg-rose-500/10 text-rose-400 text-sm px-3 py-2 ring-1 ring-rose-500/30">{{ error }}</p>
    </section>

    <section class="rounded-2xl bg-zinc-900/70 border border-white/[0.06] p-6 space-y-5">
      <div v-if="loading" class="text-sm text-zinc-500 py-4 text-center">加载中…</div>
      <template v-else>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="kb in kbs"
            :key="kb.id"
            class="px-4 py-1.5 rounded-full border text-sm transition"
            :class="selectedKb === kb.id
              ? 'bg-violet-500/15 text-violet-300 border-violet-500/40 font-medium'
              : 'text-zinc-400 border-white/10 hover:border-violet-500/40 hover:text-violet-300'"
            @click="select(kb.id)"
          >
            {{ kb.name }}
          </button>
          <span v-if="kbs.length === 0" class="text-sm text-zinc-500 py-1">还没有知识库，先在上方创建一个。</span>
        </div>

        <div v-if="selectedKb !== null" class="space-y-4">
          <h3 class="text-sm font-medium text-zinc-300">
            「{{ selectedKbName }}」的文档
          </h3>

          <label
            class="inline-flex items-center gap-2 border border-dashed border-white/15 rounded-lg px-5 py-3 text-sm text-zinc-400 cursor-pointer transition hover:border-violet-500/50 hover:text-violet-300"
          >
            {{ uploading ? '上传解析中…' : '上传文档（pdf / docx / txt / md）' }}
            <input type="file" class="hidden" accept=".pdf,.docx,.txt,.md" @change="onUpload" />
          </label>

          <p v-if="documents.length === 0" class="text-sm text-zinc-500">还没有文档。</p>

          <ul v-else class="divide-y divide-white/[0.06]">
            <li v-for="d in documents" :key="d.id" class="flex items-center gap-4 py-2.5">
              <span class="text-sm text-zinc-200 flex-1">{{ d.filename }}</span>
              <span class="text-xs text-zinc-600">{{ d.charCount }} 字符</span>
              <span class="text-xs text-zinc-600">{{ d.chunkCount }} 块</span>
              <StatusPill :status="d.status" />
            </li>
          </ul>

          <p class="text-xs text-zinc-600">
            状态说明：待处理 → 向量化中 → 已向量化（可被 RAG 检索）；失败可重新上传同名文件。
          </p>
        </div>
      </template>
    </section>
  </div>
</template>
