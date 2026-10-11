<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useResource } from '../composables'
import { dateTime, number, percent, statusLabel } from '../format'
import type { MonitorDetail } from '../types'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'
import HistoryBar from '../components/HistoryBar.vue'
import LatencyChart from '../components/LatencyChart.vue'
import LoadingState from '../components/LoadingState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
const route = useRoute()
const { data, loading, refreshing, error, reload } = useResource<MonitorDetail>(() => `/public/monitors/${route.params.id}`, 30000)
const points = computed(() => data.value?.checks?.map(c => ({ at: c.checkedAt, status: c.status, latencyMs: c.latencyMs })) || data.value?.history || [])
</script>
<template>
  <RouterLink to="/" class="back-link"><Icon name="back" :size="16" />返回服务总览</RouterLink>
  <LoadingState v-if="loading" /><ErrorBanner v-if="error" :message="error" :stale="!!data" @retry="reload" />
  <template v-if="data"><div class="page-heading detail-heading"><div><span class="eyebrow">SERVICE DETAIL / {{ String(data.id).padStart(3, '0') }}</span><h1>{{ data.name }}<StatusBadge :status="data.status" /></h1><p>{{ data.description || '持续观测服务的运行状态' }}</p><span class="monitor-url"><Icon name="globe" :size="14" />{{ data.url }}</span></div><button class="btn" :disabled="refreshing" @click="reload"><Icon name="refresh" :size="16" :class="{ spinning: refreshing }" />刷新状态</button></div>
    <div class="metrics-grid three"><article class="metric-card"><div class="metric-label">近 24 小时可用率<Icon name="shield" :size="18" /></div><div class="metric-value">{{ percent(data.uptime24h) }}</div><div class="metric-foot">已采集探测中的成功比例</div></article><article class="metric-card"><div class="metric-label">平均探测耗时<Icon name="bolt" :size="18" /></div><div class="metric-value">{{ number(data.avgLatencyMs) }}<span v-if="data.avgLatencyMs != null">ms</span></div><div class="metric-foot">近 24 小时 · 包含失败探测</div></article><article class="metric-card"><div class="metric-label">探测间隔<Icon name="clock" :size="18" /></div><div class="metric-value">{{ data.intervalSeconds }}<span>秒</span></div><div class="metric-foot">最近探测：{{ dateTime(data.lastCheckedAt, true) }}</div></article></div>
    <section class="panel"><div class="panel-heading"><div><h2>可用性历史</h2><p>每一个色块代表一次真实探测</p></div><div class="history-legend"><span><i class="green" />正常</span><span><i class="red" />异常</span><span><i class="gray" />暂无数据</span></div></div><div class="detail-history"><HistoryBar :history="points" labels /></div></section>
    <section class="panel"><div class="panel-heading"><div><h2>探测耗时</h2><p>最近 90 次包含耗时记录的探测 · 单位 ms</p></div><span class="chart-key"><i />探测耗时</span></div><div class="chart-container large"><LatencyChart :points="points" /></div></section>
    <section class="panel"><div class="panel-heading"><div><h2>探测日志<span class="count-label">{{ data.checks?.length || 0 }}</span></h2><p>最新 100 条，按探测时间倒序显示</p></div><span class="subtle-tag">只读记录</span></div><div v-if="!data.checks?.length" class="empty-state"><Icon name="clock" :size="28" /><h3>等待首次探测</h3><p>探测完成后将在此显示结果与实际耗时。</p></div><div v-else class="table-scroll"><table><thead><tr><th>探测时间</th><th>结果</th><th>探测耗时</th><th>HTTP 状态码</th><th>详情</th></tr></thead><tbody><tr v-for="check in [...data.checks].sort((a,b) => new Date(b.checkedAt).getTime() - new Date(a.checkedAt).getTime())" :key="check.id"><td class="mono">{{ dateTime(check.checkedAt) }}</td><td><StatusBadge :status="check.status" /></td><td class="mono">{{ number(check.latencyMs) }} <span class="text-muted">ms</span></td><td><code>{{ check.statusCode ?? '—' }}</code></td><td class="check-error">{{ check.error || statusLabel(check.status) }}</td></tr></tbody></table></div></section>
  </template>
</template>
