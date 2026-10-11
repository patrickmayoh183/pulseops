<script setup lang="ts">
import { computed } from 'vue'
import { dateTime, number, statusLabel } from '../format'
import type { HistoryPoint } from '../types'
const props = withDefaults(defineProps<{ history?: HistoryPoint[]; count?: number; labels?: boolean }>(), { history: () => [], count: 90, labels: false })
const points = computed(() => {
  const recent = [...props.history].sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime()).slice(-props.count)
  return [...Array(Math.max(0, props.count - recent.length)).fill(null), ...recent] as (HistoryPoint | null)[]
})
</script>
<template>
  <div class="history-wrap">
    <div class="history-bars" role="img" :aria-label="`最近 ${history.length} 次探测结果，绿色正常、红色异常、灰色无数据`">
      <span v-for="(point, index) in points" :key="index" :class="point?.status.toLowerCase() || 'empty'" :title="point ? `${dateTime(point.at)} · ${statusLabel(point.status)} · ${number(point.latencyMs)} ms` : '暂无探测数据'" />
    </div>
    <div v-if="labels" class="history-labels"><span>较早</span><span>最近 {{ Math.min(history.length, count) }} 次实际探测</span><span>现在</span></div>
  </div>
</template>
