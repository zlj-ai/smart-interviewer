<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Loader2, Lightbulb, Sparkles } from 'lucide-vue-next'

import { getAnswer, getSession, listQuestions, submitAnswer } from '@/api/sessions'
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
const error = ref('')

const selectedQuestion = computed(
  () => questions.value.find((q) => q.id === selectedId.value) ?? null,
)

const scoreColor = computed(() => {
  const score = answer.value?.score
  if (score === null || score === undefined) {
    return 'text-slate-400'
  }
  if (score >= 80) {
    return 'text-emerald-500'
  }
  if (score >= 60) {
    return 'text-amber-500'
  }
  return 'text-rose-500'
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

onMounted(load)
</script>

<template>
  <div class="space-y-5">
    <div class="flex items-center gap-3">
      <h1 class="text-lg font-semibold text-slate-800">面试 #{{ sessionId }}</h1>
      <StatusPill v-if="session" :status="session.status" />
    </div>

    <div class="grid lg:grid-cols-5 gap-5">
      <section class="lg:col-span-2 bg-white rounded-xl border border-slate-200 p-4">
        <h2 class="font-semibold text-slate-800 mb-3 px-1">题目</h2>
        <p v-if="questions.length === 0" class="text-sm text-slate-400 px-1 py-4">
          题目尚未生成（异步生成中或生成失败，可稍后刷新页面）。
        </p>
        <ul class="space-y-1">
          <li v-for="q in questions" :key="q.id">
            <button
              class="w-full text-left p-3 rounded-lg transition"
              :class="selectedId === q.id ? 'bg-indigo-50 ring-1 ring-indigo-200' : 'hover:bg-slate-50'"
              @click="select(q)"
            >
              <span class="font-mono text-xs text-slate-400 mr-1">{{ q.seq }}.</span>
              <span class="text-sm text-slate-800">{{ q.question }}</span>
              <span v-if="q.skillTag" class="inline-block ml-2 text-xs text-indigo-500 bg-indigo-50 rounded px-1.5 py-0.5">
                {{ q.skillTag }}
              </span>
            </button>
          </li>
        </ul>
      </section>

      <section class="lg:col-span-3 space-y-4">
        <div class="bg-white rounded-xl border border-slate-200 p-5 space-y-3">
          <template v-if="selectedQuestion">
            <h2 class="font-semibold text-slate-800">{{ selectedQuestion.question }}</h2>
            <textarea
              v-model="answerText"
              rows="9"
              class="w-full rounded-lg border border-slate-200 p-3 text-sm outline-none transition focus:border-indigo-400 focus:ring-4 focus:ring-indigo-100 resize-y"
              placeholder="写下你的回答…"
            />
            <div class="flex items-center justify-between">
              <span class="text-xs text-slate-400">{{ answerText.length }} 字符</span>
              <button
                :disabled="submitting || polling || !answerText.trim()"
                class="rounded-lg bg-gradient-to-r from-indigo-600 to-violet-600 text-white px-5 py-2 text-sm font-medium transition hover:opacity-95 disabled:opacity-50 flex items-center gap-2"
                @click="onSubmit"
              >
                <Loader2 v-if="submitting || polling" class="w-4 h-4 animate-spin" />
                {{ polling ? '评估中…' : '提交回答' }}
              </button>
            </div>
            <p v-if="error" class="rounded-lg bg-rose-50 text-rose-600 text-sm px-3 py-2">{{ error }}</p>
          </template>
          <p v-else class="text-sm text-slate-400 py-6 text-center">从左侧选择一道题目开始作答。</p>
        </div>

        <div v-if="answer" class="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
          <div class="flex items-center justify-between">
            <h3 class="font-semibold text-slate-800">评估结果</h3>
            <StatusPill :status="answer.status" />
          </div>

          <div v-if="answer.status === 'EVALUATED'" class="flex items-center gap-6">
            <div class="text-center">
              <p :class="['text-4xl font-bold', scoreColor]">{{ answer.score ?? '-' }}</p>
              <p class="text-xs text-slate-400 mt-1">/ 100</p>
            </div>
            <p class="text-sm text-slate-600 flex-1">{{ answer.feedback }}</p>
          </div>

          <div v-if="answer.strengths" class="flex items-start gap-2 text-sm">
            <Sparkles class="w-4 h-4 text-emerald-500 mt-0.5 shrink-0" />
            <p><span class="font-medium text-slate-700">亮点：</span>{{ answer.strengths }}</p>
          </div>
          <div v-if="answer.improvements" class="flex items-start gap-2 text-sm">
            <Lightbulb class="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
            <p><span class="font-medium text-slate-700">建议：</span>{{ answer.improvements }}</p>
          </div>
          <p v-if="polling" class="text-xs text-slate-400 flex items-center gap-2">
            <Loader2 class="w-3.5 h-3.5 animate-spin" /> 大模型评估中，通常需要几十秒…
          </p>
        </div>
      </section>
    </div>
  </div>
</template>
