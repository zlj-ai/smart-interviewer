<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { TrendingUp } from 'lucide-vue-next'

import { api } from '@/api/request'
import type { Result } from '@/types/api'

interface ScoreTrendItem {
  sessionId: number
  direction: string
  avgScore: number
  evaluatedCount: number
  sessionDate: string
}

const items = ref<ScoreTrendItem[]>([])
const loading = ref(true)
const error = ref('')

const overallAvg = computed(() => {
  if (items.value.length === 0) {
    return null
  }
  const total = items.value.reduce((sum, item) => sum + item.avgScore * item.evaluatedCount, 0)
  const evaluated = items.value.reduce((sum, item) => sum + item.evaluatedCount, 0)
  return evaluated === 0 ? null : Math.round(total / evaluated)
})

const bestScore = computed(() =>
  items.value.length === 0 ? null : Math.max(...items.value.map((i) => i.avgScore)),
)

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await api.get<Result<ScoreTrendItem[]>>('/interview/stats/score-trend')
    items.value = res.data.data as ScoreTrendItem[]
  } catch {
    error.value = '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function barColor(score: number): string {
  if (score >= 80) {
    return 'bg-emerald-500'
  }
  if (score >= 60) {
    return 'bg-teal-500'
  }
  return 'bg-red-400'
}

onMounted(load)
</script>

<template>
  <div class="space-y-6">
    <section class="card card-accent-top p-6">
      <h2 class="font-semibold text-teal-950 mb-4 flex items-center gap-2">
        <TrendingUp class="w-5 h-5 text-teal-600" /> 评分趋势
      </h2>

      <div v-if="loading" class="text-sm text-teal-900/50 py-6 text-center">加载中…</div>

      <div v-else-if="error" class="text-sm text-red-500">{{ error }}</div>

      <div v-else-if="items.length === 0"
           class="py-12 text-center border border-dashed border-teal-200 rounded-xl text-sm text-teal-900/50">
        还没有已评估的面试，完成一场后这里会显示成长曲线。
      </div>

      <template v-else>
        <div class="grid grid-cols-3 gap-4 mb-6">
          <div class="rounded-xl bg-teal-50 p-4 text-center">
            <p class="text-2xl font-bold text-teal-700">{{ overallAvg }}</p>
            <p class="text-xs text-teal-900/60 mt-1">加权平均分</p>
          </div>
          <div class="rounded-xl bg-teal-50 p-4 text-center">
            <p class="text-2xl font-bold text-teal-700">{{ bestScore }}</p>
            <p class="text-xs text-teal-900/60 mt-1">单场最佳</p>
          </div>
          <div class="rounded-xl bg-teal-50 p-4 text-center">
            <p class="text-2xl font-bold text-teal-700">{{ items.length }}</p>
            <p class="text-xs text-teal-900/60 mt-1">已完成场次</p>
          </div>
        </div>

        <div class="space-y-4">
          <div v-for="item in items" :key="item.sessionId">
            <div class="flex items-center justify-between text-sm mb-1">
              <span class="text-zinc-600">面试 #{{ item.sessionId }} · {{ item.direction }}</span>
              <span class="font-medium text-teal-800">{{ item.avgScore }} 分（{{ item.evaluatedCount }} 题）</span>
            </div>
            <div class="h-3 rounded-full bg-teal-50 overflow-hidden">
              <div class="h-full rounded-full transition-all" :class="barColor(item.avgScore)"
                   :style="{ width: item.avgScore + '%' }" />
            </div>
            <p class="text-xs text-teal-900/40 mt-1">{{ new Date(item.sessionDate).toLocaleString('zh-CN') }}</p>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
