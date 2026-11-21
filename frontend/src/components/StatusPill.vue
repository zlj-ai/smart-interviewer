<script setup lang="ts">
defineProps<{ status: string }>()

const STYLES: Record<string, string> = {
  CREATED: 'bg-sky-500/10 text-sky-400 ring-sky-500/30',
  QUESTION_GENERATING: 'bg-amber-500/10 text-amber-400 ring-amber-500/30',
  IN_PROGRESS: 'bg-violet-500/10 text-violet-300 ring-violet-500/30',
  COMPLETED: 'bg-emerald-500/10 text-emerald-400 ring-emerald-500/30',
  FAILED: 'bg-rose-500/10 text-rose-400 ring-rose-500/30',
  UPLOADED: 'bg-sky-500/10 text-sky-400 ring-sky-500/30',
  VECTORIZING: 'bg-amber-500/10 text-amber-400 ring-amber-500/30',
  VECTORIZED: 'bg-emerald-500/10 text-emerald-400 ring-emerald-500/30',
  ANALYZING: 'bg-amber-500/10 text-amber-400 ring-amber-500/30',
  ANALYZED: 'bg-emerald-500/10 text-emerald-400 ring-emerald-500/30',
}

const LABELS: Record<string, string> = {
  CREATED: '待生成题目',
  QUESTION_GENERATING: '题目生成中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  FAILED: '失败',
  UPLOADED: '待处理',
  VECTORIZING: '向量化中',
  VECTORIZED: '已向量化',
  ANALYZING: '分析中',
  ANALYZED: '已分析',
}
</script>

<template>
  <span
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset"
    :class="STYLES[status] ?? 'bg-white/5 text-zinc-400 ring-white/10'"
  >
    {{ LABELS[status] ?? status }}
  </span>
</template>
