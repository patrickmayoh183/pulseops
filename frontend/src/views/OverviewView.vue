<script setup lang="ts">
import { computed, ref } from 'vue'
import { useResource } from '../composables'
import { dateTime, number, percent } from '../format'
import type { Overview } from '../types'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'
import HistoryBar from '../components/HistoryBar.vue'
import LatencyChart from '../components/LatencyChart.vue'
import IncidentList from '../components/IncidentList.vue'
import LoadingState from '../components/LoadingState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
const { data, loading, refreshing, error, reload } = useResource<Overview>(() => '/public/overview', 30000)
const search = ref('')
const filter = ref('ALL')
const monitors = computed(() => (data.value?.monitors || []).filter(m => (filter.value === 'ALL' || m.status === filter.value) && `${m.name} ${m.description} ${m.url}`.toLowerCase().includes(search.value.toLowerCase())))
const overviewStatus = computed(() => ({ OPERATIONAL: { title: '所有服务运行正常', detail: '当前已启用的监控均处于正常状态', icon: 'check', class: 'operational' }, DEGRADED: { title: '部分服务出现异常', detail: '系统正在持续探测，请关注下方服务与事件记录', icon: 'alert', class: 'degraded' }, OUTAGE: { title: '服务出现故障', detail: '已探测的服务当前不可用，请查看事件进展', icon: 'alert', class: 'outage' }, PENDING: { title: '等待服务状态', detail: '首次探测完成后，状态与指标会自动更新', icon: 'clock', class: 'pending' } }[data.value?.overallStatus || 'PENDING']))
const chartMonitor = computed(() => data.value?.monitors.find(m => m.enabled && m.history?.some(p => p.latencyMs != null)) || data.value?.monitors[0])
const filters = [{ value: 'ALL', label: '全部服务' }, { value: 'UP', label: '正常' }, { value: 'DOWN', label: '异常' }, { value: 'PAUSED', label: '已暂停' }, { value: 'PENDING', label: '待探测' }]
</script>
<template>
  <div class="page-heading"><div><span class="eyebrow">A CLEAR VIEW OF YOUR SERVICES</span><h1>服务运行，一目了然<span class="heading-dot">.</span></h1><p>实时追踪可用性与探测耗时，让系统的每一次心跳清晰可见。</p></div><button class="btn refresh-button" :disabled="refreshing" @click="reload"><Icon name="refresh" :size="16" :class="{ spinning: refreshing }" />刷新状态</button></div>
  <ErrorBanner v-if="error" :message="error" :stale="!!data" @retry="reload" />
  <LoadingState v-if="loading" />
  <template v-if="data">
    <section class="health-banner" :class="overviewStatus.class"><div class="health-icon"><Icon :name="overviewStatus.icon" :size="28" /></div><div class="health-content"><h2>{{ overviewStatus.title }}</h2><p>{{ overviewStatus.detail }}</p></div><div class="health-updated"><span class="live-pill"><span />{{ error ? '更新中断' : '自动更新' }}</span><small>{{ dateTime(data.generatedAt) }} 更新</small></div><svg class="health-wave" viewBox="0 0 230 80" aria-hidden="true"><path d="M0 43h45l12-14 18 30 20-49 18 63 17-30h100" /></svg></section>
    <section class="metrics-grid" aria-label="核心指标">
      <article class="metric-card"><div class="metric-label">监控服务<Icon name="server" :size="18" /></div><div class="metric-value">{{ data.stats.monitorCount }}<span>个</span></div><div class="metric-foot"><span class="tiny-dot green" />{{ data.stats.operationalCount }} 个服务运行正常</div></article>
      <article class="metric-card"><div class="metric-label">近 24 小时可用率<Icon name="shield" :size="18" /></div><div class="metric-value">{{ number(data.stats.uptime24h, 2) }}<span v-if="data.stats.uptime24h != null">%</span></div><div class="metric-foot">{{ data.stats.uptime24h == null ? '等待有效探测数据' : '基于已完成的实际探测' }}</div></article>
      <article class="metric-card"><div class="metric-label">平均探测耗时<Icon name="bolt" :size="18" /></div><div class="metric-value">{{ number(data.stats.avgLatencyMs) }}<span v-if="data.stats.avgLatencyMs != null">ms</span></div><div class="metric-foot">{{ data.stats.avgLatencyMs == null ? '等待探测耗时数据' : '近 24 小时 · 包含失败探测' }}</div></article>
      <article class="metric-card"><div class="metric-label">正在处理的事件<Icon name="bell" :size="18" /></div><div class="metric-value" :class="{ 'text-danger': data.stats.openIncidentCount > 0 }">{{ data.stats.openIncidentCount }}<span>起</span></div><div class="metric-foot"><span class="tiny-dot" :class="data.stats.openIncidentCount ? 'orange' : 'green'" />{{ data.stats.openIncidentCount ? '持续跟踪故障恢复情况' : '当前没有未恢复事件' }}</div></article>
    </section>
    <section class="panel monitor-panel"><div class="panel-heading"><div><h2>服务监控<span class="count-label">{{ data.monitors.length }}</span></h2><p>每一次探测，都记录在这里</p></div><div class="history-legend"><span><i class="green" />正常</span><span><i class="red" />异常</span><span><i class="gray" />暂无数据</span></div></div><div class="monitor-toolbar"><div class="filter-tabs" role="group" aria-label="按服务状态筛选"><button v-for="item in filters" :key="item.value" :class="{ selected: filter === item.value }" :aria-pressed="filter === item.value" @click="filter = item.value">{{ item.label }}</button></div><label class="search-field"><Icon name="search" :size="16" /><input v-model="search" aria-label="搜索服务" placeholder="搜索服务名称或地址" /></label></div>
      <div v-if="!data.monitors.length" class="empty-state"><span class="empty-icon"><Icon name="server" :size="26" /></span><h3>还没有监控服务</h3><p>管理员添加监控目标后，这里将展示真实的运行状态。</p><RouterLink to="/admin/monitors" class="btn btn-primary">配置第一个监控<Icon name="arrow" :size="15" /></RouterLink></div>
      <div v-else-if="!monitors.length" class="empty-state small"><Icon name="search" :size="28" /><h3>没有匹配的服务</h3><p>尝试其他名称或状态筛选。</p><button class="text-button" @click="search = ''; filter = 'ALL'">清除筛选</button></div>
      <div v-else class="monitor-list"><RouterLink v-for="monitor in monitors" :key="monitor.id" :to="`/monitors/${monitor.id}`" class="monitor-row"><div class="monitor-identity"><span class="service-icon"><Icon name="globe" :size="21" /></span><div><h3>{{ monitor.name }}<Icon name="chevron" :size="14" /></h3><p>{{ monitor.description || monitor.url }}</p></div></div><div class="monitor-history"><HistoryBar :history="monitor.history || []" /><div class="monitor-history-meta"><span>最近 90 次探测</span><strong>{{ percent(monitor.uptime24h) }} <small>可用率 / 24h</small></strong></div></div><div class="monitor-latency"><strong>{{ number(monitor.avgLatencyMs) }}<small v-if="monitor.avgLatencyMs != null"> ms</small></strong><span>平均耗时 / 24h</span></div><StatusBadge :status="monitor.status" /></RouterLink></div>
      <div class="panel-footnote"><Icon name="info" :size="14" />状态每 30 秒自动更新 · 探测间隔由各服务独立配置<span>所有时间均为浏览器本地时间</span></div>
    </section>
    <div class="bottom-grid"><section class="panel"><div class="panel-heading"><div><h2>探测耗时趋势</h2><p>{{ chartMonitor ? `${chartMonitor.name} · 最近 ${Math.min(90, chartMonitor.history?.length || 0)} 次探测` : '等待配置监控服务' }}</p></div><span class="chart-key"><i />探测耗时</span></div><div class="chart-container"><LatencyChart :points="chartMonitor?.history || []" /></div></section><section class="panel"><div class="panel-heading"><div><h2>最近事件</h2><p>从发现异常到服务恢复</p></div><RouterLink to="/incidents" class="panel-link">查看全部<Icon name="arrow" :size="15" /></RouterLink></div><IncidentList :incidents="data.incidents.slice(0, 3)" compact /></section></div>
    <div class="observation-note"><Icon name="shield" :size="17" /><span>指标以本系统实际采集的数据为准。新服务的可用率仅覆盖已采样时段，尚未形成完整 24 小时窗口。</span></div>
  </template>
</template>
