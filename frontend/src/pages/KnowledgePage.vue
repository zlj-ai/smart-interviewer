<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { createKb, listDocuments, listKbs, uploadDocument } from '@/api/knowledge'
import type { KbDocumentResponse, KbResponse } from '@/types/api'

const kbs = ref<KbResponse[]>([])
const selectedKb = ref<number | null>(null)
const documents = ref<KbDocumentResponse[]>([])
const name = ref('')
const description = ref('')
const uploading = ref(false)
const error = ref('')

async function loadKbs(): Promise<void> {
  kbs.value = await listKbs()
  if (selectedKb.value === null && kbs.value.length > 0) {
    await select(kbs.value[0].id)
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
    <section class="bg-white rounded-lg border border-slate-200 p-6 space-y-3">
      <h2 class="font-semibold">创建知识库</h2>
      <div class="flex items-end gap-3">
        <input v-model="name" placeholder="名称" class="border rounded px-3 py-2" />
        <input v-model="description" placeholder="描述（可选）" class="border rounded px-3 py-2 flex-1" />
        <button class="bg-indigo-600 text-white rounded px-4 py-2" @click="onCreate">创建</button>
      </div>
      <p v-if="error" class="text-sm text-red-500">{{ error }}</p>
    </section>

    <section class="bg-white rounded-lg border border-slate-200 p-6 space-y-3">
      <h2 class="font-semibold">我的知识库</h2>
      <div class="flex flex-wrap gap-2">
        <button v-for="kb in kbs" :key="kb.id"
                class="px-3 py-1.5 rounded-full border text-sm"
                :class="selectedKb === kb.id ? 'bg-indigo-600 text-white border-indigo-600' : 'hover:border-indigo-400'"
                @click="select(kb.id)">
          {{ kb.name }}
        </button>
        <span v-if="kbs.length === 0" class="text-sm text-slate-400">还没有知识库，先创建一个。</span>
      </div>

      <div v-if="selectedKb !== null">
        <label class="inline-block border border-dashed rounded px-4 py-2 text-sm cursor-pointer hover:border-indigo-400">
          {{ uploading ? '上传解析中…' : '上传文档（pdf / docx / txt / md）' }}
          <input type="file" class="hidden" accept=".pdf,.docx,.txt,.md" @change="onUpload" />
        </label>

        <table class="w-full text-sm mt-3">
          <thead>
            <tr class="text-left text-slate-500 border-b">
              <th class="py-2">文件</th><th>字符数</th><th>分块</th><th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in documents" :key="d.id" class="border-b">
              <td class="py-2">{{ d.filename }}</td>
              <td>{{ d.charCount }}</td>
              <td>{{ d.chunkCount }}</td>
              <td>{{ d.status }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-xs text-slate-400 mt-2">
          状态说明：UPLOADED=待向量化，VECTORIZING=向量化中，VECTORIZED=可检索，FAILED=失败。
        </p>
      </div>
    </section>
  </div>
</template>
