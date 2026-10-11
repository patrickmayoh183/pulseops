<script setup lang="ts">
import { computed } from 'vue'
import { clockTime, dateTime, number } from '../format'
import type { HistoryPoint } from '../types'
import Icon from './Icon.vue'
const props = defineProps<{ points: HistoryPoint[] }>()
const samples = computed(() => [...props.points].filter(p => p.latencyMs != null).sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime()).slice(-90))
const max = computed(() => Math.max(1, ...samples.value.map(p => p.latencyMs || 0)) * 1.2)
const coordinates = computed(() => {
  const start = new Date(samples.value[0]?.at || 0).getTime()
  const end = new Date(samples.value[samples.value.length - 1]?.at || 0).getTime()
  return samples.value.map(p => ({ x: start === end ? 352 : 12 + (new Date(p.at).getTime() - start) / (end - start) * 676, y: 150 - (p.latencyMs || 0) / max.value * 130, point: p }))
})
const line = computed(() => coordinates.value.map(p => `${p.x},${p.y}`).join(' '))
const area = computed(() => coordinates.value.length ? `M${coordinates.value[0]!.x},150 L${line.value.replaceAll(' ', ' L')} L${coordinates.value[coordinates.value.length - 1]!.x},150 Z` : '')
</script>
<template>
  <div v-if="!samples.length" class="chart-empty"><Icon name="activity" :size="30" /><span>等待探测耗时数据</span><small>完成首次探测后，趋势将在这里呈现</small></div>
  <div v-else class="latency-chart">
    <div class="chart-axis"><span>{{ number(max) }} ms</span><span>{{ number(max / 2) }} ms</span><span>0 ms</span></div>
    <div class="chart-plot">
      <svg viewBox="0 0 700 166" role="img" aria-label="最近实际探测耗时趋势，包含失败探测" preserveAspectRatio="none">
        <path d="M0 20h700 M0 85h700 M0 150h700" class="chart-grid" />
        <path :d="area" fill="rgba(20, 139, 103, 0.075)" />
        <polyline :points="line" fill="none" stroke="#158465" stroke-width="2.5" vector-effect="non-scaling-stroke" stroke-linecap="round" stroke-linejoin="round" />
        <circle v-for="(p, index) in coordinates" :key="index" :cx="p.x" :cy="p.y" :r="coordinates.length < 3 ? 4 : 2" fill="#158465"><title>{{ dateTime(p.point.at) }} · {{ number(p.point.latencyMs) }} ms</title></circle>
      </svg>
      <div class="chart-time"><span>{{ clockTime(samples[0]?.at) }}</span><span>{{ samples.length }} 个有耗时记录的样本</span><span>{{ clockTime(samples[samples.length - 1]?.at) }}</span></div>
    </div>
  </div>
  <p class="chart-scope-note">从 DNS 解析到探测结束的总耗时，失败探测也计入。</p>
</template>
