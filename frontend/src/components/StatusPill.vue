<script setup lang="ts">
defineProps<{ status: string }>()

/* 薄荷青浅色徽章：柔和色底 + 深色文字 */
const STYLES: Record<string, string> = {
  CREATED: 'bg-sky-100 text-sky-700',
  QUESTION_GENERATING: 'bg-amber-100 text-amber-700',
  IN_PROGRESS: 'bg-teal-100 text-teal-800',
  COMPLETED: 'bg-emerald-100 text-emerald-700',
  FAILED: 'bg-red-100 text-red-700',
  UPLOADED: 'bg-sky-100 text-sky-700',
  VECTORIZING: 'bg-amber-100 text-amber-700',
  VECTORIZED: 'bg-emerald-100 text-emerald-700',
  ANALYZING: 'bg-amber-100 text-amber-700',
  ANALYZED: 'bg-emerald-100 text-emerald-700',
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
    class="inline-flex items-center rounded-[10px] px-2.5 py-0.5 text-xs font-medium"
    :class="STYLES[status] ?? 'bg-stone-100 text-stone-600'"
  >
    {{ LABELS[status] ?? status }}
  </span>
</template>
