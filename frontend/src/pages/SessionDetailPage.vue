<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getAnswer, getSession, listQuestions, submitAnswer } from '@/api/sessions'
import type { AnswerResponse, QuestionResponse, SessionResponse } from '@/types/api'

const route = useRoute()
const sessionId = Number(route.params.id)

const session = ref<SessionResponse | null>(null)
const questions = ref<QuestionResponse[]>([])
const selectedId = ref<number | null>(null)
const answerText = ref('')
const answer = ref<AnswerResponse | null>(null)
const submitting = ref(false)
const error = ref('')

const selectedQuestion = computed(() => questions.value.find((q) => q.id === selectedId.value) ?? null)

async function load(): Promise<void> {
  session.value = await getSession(sessionId)
  questions.value = await listQuestions(sessionId)
}

function select(question: QuestionResponse): void {
  selectedId.value = question.id
  answer.value = null
  error.value = ''
  void loadAnswer(question.id)
}

async function loadAnswer(questionId: number): Promise<void> {
  try {
    answer.value = await getAnswer(sessionId, questionId)
    if (answer.value.answerText) {
      answerText.value = answer.value.answerText
    }
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
  // LLM 评估需要几十秒，轮询直到终态
  for (let i = 0; i < 40; i++) {
    await new Promise((resolve) => setTimeout(resolve, 3000))
    const result = await getAnswer(sessionId, questionId)
    if (result.status === 'EVALUATED' || result.status === 'FAILED') {
      answer.value = result
      return
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-lg font-semibold">面试 #{{ sessionId }}
      <span class="text-sm text-slate-400 ml-2">{{ session?.status }}</span>
    </h1>

    <div class="grid grid-cols-3 gap-6">
      <section class="bg-white rounded-lg border border-slate-200 p-4">
        <h2 class="font-semibold mb-2">题目</h2>
        <p v-if="questions.length === 0" class="text-sm text-slate-400">
          题目尚未生成（异步生成中或生成失败，可稍后刷新）。
        </p>
        <ul class="space-y-1">
          <li v-for="q in questions" :key="q.id"
              class="p-2 rounded cursor-pointer hover:bg-slate-100"
              :class="{ 'bg-indigo-50': selectedId === q.id }" @click="select(q)">
            <span class="text-slate-400 mr-1">{{ q.seq }}.</span>{{ q.question }}
            <span v-if="q.skillTag" class="text-xs text-indigo-500 ml-1">{{ q.skillTag }}</span>
          </li>
        </ul>
      </section>

      <section class="col-span-2 bg-white rounded-lg border border-slate-200 p-4 space-y-3">
        <template v-if="selectedQuestion">
          <h2 class="font-semibold">{{ selectedQuestion.question }}</h2>
          <textarea v-model="answerText" rows="8" class="w-full border rounded p-3 text-sm"
                    placeholder="写下你的回答…" />
          <button :disabled="submitting" class="bg-indigo-600 text-white rounded px-4 py-2 disabled:opacity-50"
                  @click="onSubmit">
            {{ submitting ? '评估中…' : '提交回答' }}
          </button>
          <p v-if="error" class="text-sm text-red-500">{{ error }}</p>

          <div v-if="answer" class="border-t pt-3 space-y-2 text-sm">
            <p><span class="text-slate-500">状态：</span>{{ answer.status }}</p>
            <p v-if="answer.score !== null"><span class="text-slate-500">评分：</span>{{ answer.score }} / 100</p>
            <p v-if="answer.feedback"><span class="text-slate-500">评价：</span>{{ answer.feedback }}</p>
            <p v-if="answer.strengths"><span class="text-slate-500">亮点：</span>{{ answer.strengths }}</p>
            <p v-if="answer.improvements"><span class="text-slate-500">建议：</span>{{ answer.improvements }}</p>
          </div>
        </template>
        <p v-else class="text-sm text-slate-400">从左侧选择一道题目开始作答。</p>
      </section>
    </div>
  </div>
</template>
