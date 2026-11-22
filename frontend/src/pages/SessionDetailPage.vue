<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Download, Loader2, Lightbulb, Sparkles } from 'lucide-vue-next'

import { api } from '@/api/request'
import { getAnswer, getSession, listQuestions, submitAnswer } from '@/api/sessions'
import { regenerateQuestions } from '@/api/sessions'
import StatusPill from '@/components/StatusPill.vue'
import type { AnswerResponse, QuestionResponse, SessionResponse } from '@/types/api'

const route = useRoute()
const sessionId = Number(route.params.id)

const session = ref<SessionResponse | null>(null)
const questions = ref<QuestionResponse[]>([])
const selectedId = ref<number | null>(null)
const answerText = ref('')
const answer = ref<AnswerResponse | null>(null)
const polling = ref(false)
const submitting = ref(false)
const regenerating = ref(false)
const exporting = ref(false)
const error = ref('')

const selectedQuestion = computed(
  () => questions.value.find((q) => q.id === selectedId.value) ?? null,
)

const scoreStyle = computed(() => {
  const score = answer.value?.score
  if (score === null || score === undefined) {
    return 'text-teal-900/30'
  }
  if (score >= 80) {
    return 'text-emerald-600'
  }
  if (score >= 60) {
    return 'text-amber-600'
  }
  return 'text-red-500'
})

async function load(): Promise<void> {
  session.value = await getSession(sessionId)
  questions.value = await listQuestions(sessionId)
}

async function select(question: QuestionResponse): Promise<void> {
  selectedId.value = question.id
  answer.value = null
  error.value = ''
  answerText.value = ''
  try {
    const existing = await getAnswer(sessionId, question.id)
    answer.value = existing
    answerText.value = existing.answerText
  } catch {
    answer.value = null
  }
}

async function onSubmit(): Promise<void> {
  if (!selectedId.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    answer.value = await submitAnswer(sessionId, selectedId.value, answerText.value)
    await pollEvaluation(selectedId.value)
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message
      ?? '提交失败'
  } finally {
    submitting.value = false
  }
}

async function onRegenerate(): Promise<void> {
  regenerating.value = true
  error.value = ''
  try {
    session.value = await regenerateQuestions(sessionId)
    questions.value = []
  } catch (e: unknown) {
    error.value = (e as { response?: { data?: { message?: string } } }).response?.data?.message
      ?? '重新生成失败'
  } finally {
    regenerating.value = false
  }
}

async function pollEvaluation(questionId: number): Promise<void> {
  polling.value = true
  try {
    for (let i = 0; i < 40; i++) {
      await new Promise((resolve) => setTimeout(resolve, 3000))
      const result = await getAnswer(sessionId, questionId)
      if (result.status === 'EVALUATED' || result.status === 'FAILED') {
        answer.value = result
        return
      }
    }
  } finally {
    polling.value = false
  }
}

async function exportReport(): Promise<void> {
  exporting.value = true
  error.value = ''
  try {
    const res = await api.get(`/interview/sessions/${sessionId}/report.pdf`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `面试报告-${sessionId}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    error.value = '报告导出失败，请稍后重试'
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-5">
    <div class="flex items-center gap-3">
      <h1 class="text-lg font-semibold text-teal-950">面试 #{{ sessionId }}</h1>
      <StatusPill v-if="session" :status="session.status" />
      <button
        v-if="session && (session.status === 'FAILED' || session.status === 'COMPLETED')"
        :disabled="regenerating"
        class="text-sm text-teal-700 border border-teal-200 rounded-lg px-3 py-1.5 transition hover:bg-teal-50 disabled:opacity-50"
        @click="onRegenerate"
      >
        {{ regenerating ? '重新生成中…' : '重新生成题目' }}
      </button>
      <button
        :disabled="exporting"
        class="flex items-center gap-1 text-sm text-teal-700 border border-teal-200 rounded-lg px-3 py-1.5 transition hover:bg-teal-50 disabled:opacity-50"
        @click="exportReport"
      >
        <Download v-if="!exporting" class="w-3.5 h-3.5" />
        <Loader2 v-else class="w-3.5 h-3.5 animate-spin" />
        导出报告 PDF
      </button>
    </div>

    <p v-if="error" class="rounded-xl bg-red-50 text-red-600 text-sm px-3 py-2 border border-red-100">{{ error }}</p>

    <div class="grid lg:grid-cols-5 gap-5">
      <section class="lg:col-span-2 card p-4">
        <h2 class="font-semibold text-teal-950 mb-3 px-1">题目</h2>
        <p v-if="questions.length === 0" class="text-sm text-teal-900/50 px-1 py-4">
          题目尚未生成（异步生成中或生成失败，可稍后刷新页面）。
        </p>
        <ul class="space-y-1">
          <li v-for="q in questions" :key="q.id">
            <button
              class="w-full text-left p-3 rounded-lg transition"
              :class="selectedId === q.id ? 'bg-teal-50 ring-1 ring-teal-200' : 'hover:bg-teal-50/60'"
              @click="select(q)"
            >
              <span class="font-mono text-xs text-teal-900/40 mr-1">{{ q.seq }}.</span>
              <span class="text-sm text-teal-950">{{ q.question }}</span>
              <span v-if="q.skillTag" class="inline-block ml-2 text-xs text-teal-700 bg-teal-100/80 rounded px-1.5 py-0.5">
                {{ q.skillTag }}
              </span>
            </button>
          </li>
        </ul>
      </section>

      <section class="lg:col-span-3 space-y-4">
        <div class="card p-5 space-y-3">
          <template v-if="selectedQuestion">
            <h2 class="font-semibold text-teal-950">{{ selectedQuestion.question }}</h2>
            <textarea
              v-model="answerText"
              rows="9"
              class="w-full rounded-xl border border-teal-100 bg-white p-3 text-sm text-teal-950 placeholder-teal-900/40 outline-none transition focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10 resize-y"
              placeholder="写下你的回答…"
            />
            <div class="flex items-center justify-between">
              <span class="text-xs text-teal-900/40">{{ answerText.length }} 字符</span>
              <button
                :disabled="submitting || polling || !answerText.trim()"
                class="btn-primary flex items-center gap-2"
                @click="onSubmit"
              >
                <Loader2 v-if="submitting || polling" class="w-4 h-4 animate-spin" />
                {{ polling ? '评估中…' : '提交回答' }}
              </button>
            </div>
            <p v-if="error" class="rounded-xl bg-red-50 text-red-600 text-sm px-3 py-2 border border-red-100">{{ error }}</p>
          </template>
          <p v-else class="text-sm text-teal-900/50 py-6 text-center">从左侧选择一道题目开始作答。</p>
        </div>

        <div v-if="answer" class="card p-5 space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="font-semibold text-teal-950">评估结果</h3>
            <StatusPill :status="answer.status" />
          </div>

          <div v-if="answer.status === 'EVALUATED'" class="flex items-center gap-6">
            <div class="text-center">
              <p class="text-5xl font-bold font-mono" :class="scoreStyle">{{ answer.score ?? '-' }}</p>
              <p class="text-xs text-teal-900/40 mt-1">/ 100</p>
            </div>
            <p class="text-sm text-teal-950/80 flex-1 leading-relaxed">{{ answer.feedback }}</p>
          </div>

          <div v-if="answer.strengths" class="rounded-xl bg-emerald-50 border border-emerald-100 p-3.5 flex items-start gap-2.5 text-sm">
            <Sparkles class="w-4 h-4 text-emerald-500 mt-0.5 shrink-0" />
            <p class="text-emerald-900/90"><span class="font-medium">亮点：</span>{{ answer.strengths }}</p>
          </div>
          <div v-if="answer.improvements" class="rounded-xl bg-amber-50 border border-amber-100 p-3.5 flex items-start gap-2.5 text-sm">
            <Lightbulb class="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
            <p class="text-amber-900/90"><span class="font-medium">建议：</span>{{ answer.improvements }}</p>
          </div>
          <p v-if="polling" class="text-xs text-teal-900/50 flex items-center gap-2">
            <Loader2 class="w-3.5 h-3.5 animate-spin" /> 大模型评估中，通常需要几十秒…
          </p>
        </div>
      </section>
    </div>
  </div>
</template>
