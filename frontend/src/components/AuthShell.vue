<script setup lang="ts">
import { BookOpen, Bot, MessagesSquare, Star } from 'lucide-vue-next'

defineProps<{
  title: string
  subtitle: string
}>()

const FEATURES = [
  { icon: MessagesSquare, title: 'AI 模拟面试', text: '十个方向的题目生成与追问' },
  { icon: Star, title: '回答智能评估', text: '逐题打分与改进建议' },
  { icon: BookOpen, title: 'RAG 知识库', text: '基于你的资料回答并标注来源' },
]
</script>

<template>
  <div class="hidden lg:flex flex-col justify-center bg-gradient-to-br from-indigo-600 via-violet-600 to-fuchsia-600 px-14 text-white relative overflow-hidden">
    <div class="absolute -top-24 -right-24 w-96 h-96 bg-white/10 rounded-full blur-2xl" />
    <div class="absolute -bottom-32 -left-16 w-96 h-96 bg-fuchsia-400/20 rounded-full blur-2xl" />

    <div class="relative">
      <div class="flex items-center gap-3">
        <div class="w-11 h-11 rounded-xl bg-white/15 backdrop-blur flex items-center justify-center ring-1 ring-white/30">
          <Bot class="w-6 h-6" />
        </div>
        <span class="text-xl font-semibold tracking-wide">Smart Interviewer</span>
      </div>

      <h1 class="mt-10 text-4xl font-bold leading-snug">
        用 AI 把下一次面试<br />练成「已经面过」
      </h1>
      <p class="mt-4 text-indigo-100">{{ subtitle }}</p>

      <ul class="mt-12 space-y-6">
        <li v-for="f in FEATURES" :key="f.title" class="flex items-start gap-4">
          <div class="w-9 h-9 rounded-lg bg-white/15 ring-1 ring-white/25 flex items-center justify-center shrink-0">
            <component :is="f.icon" class="w-5 h-5 opacity-90" />
          </div>
          <div>
            <p class="font-medium">{{ f.title }}</p>
            <p class="text-sm text-indigo-100 mt-0.5">{{ f.text }}</p>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>
