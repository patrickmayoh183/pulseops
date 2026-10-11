<script setup lang="ts">
import { computed, ref } from 'vue'
import { useResource } from '../composables'
import type { Overview } from '../types'
import Icon from '../components/Icon.vue'
import IncidentList from '../components/IncidentList.vue'
import LoadingState from '../components/LoadingState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
const { data, loading, refreshing, error, reload } = useResource<Overview>(() => '/public/overview', 30000)
const filter = ref('ALL')
const incidents = computed(() => (data.value?.incidents || []).filter(i => filter.value === 'ALL' || i.status === filter.value))
</script>
<template>
  <div class="page-heading"><div><span class="eyebrow">EVERY EVENT HAS A TIMELINE</span><h1>从异常发现，到服务恢复<span class="heading-dot">.</span></h1><p>公开、透明的事件记录，完整追踪每一次状态变化。</p></div><button class="btn" :disabled="refreshing" @click="reload"><Icon name="refresh" :size="16" :class="{ spinning: refreshing }" />刷新记录</button></div>
  <ErrorBanner v-if="error" :message="error" :stale="!!data" @retry="reload" /><LoadingState v-if="loading" />
  <template v-if="data"><section class="info-strip"><Icon name="info" /><p>连续 <strong>2 次失败</strong>自动创建故障事件，连续 <strong>2 次成功</strong>自动恢复。此处展示最近 20 起事件。</p></section><section class="panel"><div class="panel-heading"><div><h2>事件时间线<span class="count-label">{{ incidents.length }}</span></h2><p>自动探测与人工进展，汇聚在同一条时间线</p></div><div class="filter-tabs"><button v-for="item in [{ value: 'ALL', label: '全部' }, { value: 'OPEN', label: '处理中' }, { value: 'RESOLVED', label: '已恢复' }]" :key="item.value" :class="{ selected: filter === item.value }" :aria-pressed="filter === item.value" @click="filter = item.value">{{ item.label }}</button></div></div><IncidentList v-if="incidents.length || filter === 'ALL'" :incidents="incidents" /><div v-else class="empty-state"><Icon name="check" :size="28" /><h3>没有{{ filter === 'OPEN' ? '处理中' : '已恢复' }}的事件</h3><p>状态变化会在这里自动记录。</p></div></section></template>
</template>
