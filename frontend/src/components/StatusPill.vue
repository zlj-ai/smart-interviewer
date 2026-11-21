<script setup lang="ts">
defineProps<{ status: string }>()

const STYLES: Record<string, string> = {
  CREATED: 'bg-sky-50 text-sky-700 ring-sky-200',
  QUESTION_GENERATING: 'bg-amber-50 text-amber-700 ring-amber-200',
  IN_PROGRESS: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  FAILED: 'bg-rose-50 text-rose-700 ring-rose-200',
  UPLOADED: 'bg-sky-50 text-sky-700 ring-sky-200',
  VECTORIZING: 'bg-amber-50 text-amber-700 ring-amber-200',
  VECTORIZED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  ANALYZING: 'bg-amber-50 text-amber-700 ring-amber-200',
  ANALYZED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
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
    :class="STYLES[status] ?? 'bg-slate-50 text-slate-600 ring-slate-200'"
  >
    {{ LABELS[status] ?? status }}
  </span>
</template>
