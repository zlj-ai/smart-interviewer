<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  icon: Component
  text: string
}>()
</script>

<template>
  <div class="py-12 text-center border border-dashed border-teal-200 rounded-xl bg-teal-50/30 relative overflow-hidden">
    <!-- 装饰图案：角部同心圆 -->
    <div class="absolute -top-6 -right-6 w-24 h-24 rounded-full border-[6px] border-teal-100/70" />
    <div class="absolute -bottom-8 -left-8 w-28 h-28 rounded-full border-[6px] border-teal-100/50" />

    <div class="relative">
      <div class="w-14 h-14 mx-auto rounded-2xl bg-white shadow-md shadow-teal-600/10 border border-teal-100 flex items-center justify-center">
        <div class="w-9 h-9 rounded-lg bg-teal-50 flex items-center justify-center">
          <component :is="icon" class="w-5 h-5 text-teal-600" />
        </div>
      </div>
      <p class="mt-4 text-sm text-teal-900/60">{{ text }}</p>
      <slot />
    </div>
  </div>
</template>
