<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useResource } from '../composables'
import { api, errorMessage, notify, session } from '../api'
import { dateTime, number, duration } from '../format'
import type { Monitor, Incident, Audit, SystemInfo } from '../types'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'
import IncidentList from '../components/IncidentList.vue'
import LoadingState from '../components/LoadingState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import MonitorForm from '../components/MonitorForm.vue'
import IncidentUpdateForm from '../components/IncidentUpdateForm.vue'
const route = useRoute()
const tab = computed(() => String(route.params.tab))
const tabs = [{ id: 'monitors', title: '监控管理', icon: 'server' }, { id: 'incidents', title: '事件管理', icon: 'bell' }, { id: 'audit', title: '操作审计', icon: 'list' }, { id: 'system', title: '系统信息', icon: 'code' }]
const { data, loading, refreshing, error, reload } = useResource<Monitor[] | Incident[] | Audit[] | SystemInfo>(() => `/admin/${tab.value}`, 30000)
const search = ref('')
const showForm = ref(false)
const editMonitor = ref<Monitor | null>(null)
const updateIncident = ref<Incident | null>(null)
const busy = ref<string | null>(null)
const monitors = computed(() => tab.value === 'monitors' && Array.isArray(data.value) ? (data.value as Monitor[]).filter(m => `${m.name} ${m.url}`.toLowerCase().includes(search.value.toLowerCase())) : [])
const incidents = computed(() => tab.value === 'incidents' && Array.isArray(data.value) ? data.value as Incident[] : [])
const audits = computed(() => tab.value === 'audit' && Array.isArray(data.value) ? data.value as Audit[] : [])
const system = computed(() => tab.value === 'system' && data.value && !Array.isArray(data.value) ? data.value as SystemInfo : null)
const auditLabels: Record<string, string> = { LOGIN: '登录', LOGIN_FAILED: '登录失败', LOGOUT: '退出登录', MONITOR_CREATED: '创建监控', MONITOR_UPDATED: '更新监控', MONITOR_RESUMED: '启用监控', MONITOR_PAUSED: '暂停监控', CHECK_REQUESTED: '手动探测', INCIDENT_UPDATED: '发布事件进展' }
function openForm(monitor: Monitor | null = null) { editMonitor.value = monitor; showForm.value = true }
async function afterSaved() { showForm.value = false; updateIncident.value = null; await reload() }
async function toggleMonitor(monitor: Monitor) {
  if (busy.value) return
  busy.value = `toggle-${monitor.id}`
  try { await api(`/admin/monitors/${monitor.id}/enabled`, 'PATCH', { enabled: !monitor.enabled }); notify(monitor.enabled ? '监控已暂停' : '监控已启用'); await reload() } catch (e) { notify(errorMessage(e), 'error') } finally { busy.value = null }
}
async function checkMonitor(monitor: Monitor) {
  if (busy.value) return
  busy.value = `check-${monitor.id}`
  try { await api(`/admin/monitors/${monitor.id}/check`, 'POST'); notify('探测请求已提交，结果将在后续刷新中显示'); await reload() } catch (e) { notify(errorMessage(e), 'error') } finally { busy.value = null }
}
</script>
<template>
  <div class="page-heading"><div><span class="eyebrow">MANAGE WITH CONFIDENCE</span><h1>把运行状态，握在手中<span class="heading-dot">.</span></h1><p>配置探测策略、追踪事件进展，记录每一次管理操作。</p></div><RouterLink to="/" class="btn"><Icon name="external" :size="16" />公开状态页</RouterLink></div>
  <nav class="admin-tabs" aria-label="管理功能"><RouterLink v-for="item in tabs" :key="item.id" :to="`/admin/${item.id}`" :class="{ selected: tab === item.id }"><Icon :name="item.icon" :size="18" />{{ item.title }}</RouterLink></nav>
  <ErrorBanner v-if="error" :message="error" :stale="!!data" @retry="reload" /><div v-if="!session?.authenticated" class="info-strip"><Icon name="lock" /><p>管理会话已失效，<RouterLink :to="{ path: '/login', query: { redirect: route.fullPath } }">请重新登录</RouterLink>。</p></div><LoadingState v-if="loading" label="正在加载管理数据…" />
  <section v-if="tab === 'monitors'" class="panel"><div class="panel-heading"><div><h2>监控服务<span v-if="data && Array.isArray(data)" class="count-label">{{ data.length }}</span></h2><p>配置目标与探测策略，实时关注服务运行</p></div><button class="btn btn-primary" @click="openForm()"><Icon name="plus" :size="17" />添加监控</button></div><div class="admin-toolbar"><label class="search-field"><Icon name="search" :size="16" /><input v-model="search" aria-label="搜索监控服务" placeholder="搜索名称或监控地址" /></label><button class="btn btn-small" :disabled="refreshing" @click="reload"><Icon name="refresh" :size="15" :class="{ spinning: refreshing }" />刷新</button></div>
    <div v-if="!loading && data && !monitors.length" class="empty-state"><Icon name="server" :size="30" /><h3>{{ search ? '没有匹配的监控' : '添加你的第一个监控' }}</h3><p>{{ search ? '尝试搜索其他名称或地址。' : '从一个可公开访问的 HTTPS 服务开始，持续记录真实状态。' }}</p></div><div v-if="monitors.length" class="table-scroll"><table class="admin-monitor-table"><thead><tr><th>服务信息</th><th>当前状态</th><th>探测策略</th><th>最近探测</th><th class="actions-cell">操作</th></tr></thead><tbody><tr v-for="monitor in monitors" :key="monitor.id"><td><RouterLink class="table-service" :to="`/monitors/${monitor.id}`">{{ monitor.name }}<Icon name="external" :size="13" /></RouterLink><span class="table-url" :title="monitor.url">{{ monitor.url }}</span></td><td><StatusBadge :status="monitor.status" /></td><td><span class="strategy">每 {{ monitor.intervalSeconds }} 秒</span><span class="table-secondary">预期 {{ monitor.expectedStatus ?? 200 }} · 超时 {{ number((monitor.timeoutMs ?? 5000) / 1000, 1) }}s</span></td><td class="mono">{{ dateTime(monitor.lastCheckedAt, true) }}</td><td><div class="table-actions"><button class="icon-btn" :disabled="busy !== null || !monitor.enabled" :title="monitor.enabled ? '立即探测' : '请先启用监控'" :aria-label="`立即探测 ${monitor.name}`" @click="checkMonitor(monitor)"><Icon name="play" :size="16" :class="{ spinning: busy === `check-${monitor.id}` }" /></button><button class="icon-btn" :disabled="busy !== null" :title="monitor.enabled ? '暂停监控' : '启用监控'" :aria-label="`${monitor.enabled ? '暂停' : '启用'} ${monitor.name}`" @click="toggleMonitor(monitor)"><Icon :name="monitor.enabled ? 'pause' : 'refresh'" :size="16" /></button><button class="icon-btn" :disabled="busy !== null" title="编辑配置" :aria-label="`编辑 ${monitor.name}`" @click="openForm(monitor)"><Icon name="edit" :size="16" /></button></div></td></tr></tbody></table></div><div class="panel-footnote"><Icon name="info" :size="14" />目标受服务器域名白名单约束，配置变更会写入审计日志。</div>
  </section>
  <template v-if="tab === 'incidents' && data"><section class="info-strip"><Icon name="info" /><p>事件由探测结果自动创建和恢复。你可以添加处理进展，内容会立即展示在公开状态页。</p></section><section v-if="!incidents.length" class="panel"><IncidentList :incidents="[]" /></section><section v-for="incident in incidents" :key="incident.id" class="panel admin-incident"><div class="admin-incident-top"><span class="eyebrow">INCIDENT #{{ incident.id }}</span><button class="btn btn-small" @click="updateIncident = incident"><Icon name="edit" :size="15" />记录进展</button></div><IncidentList :incidents="[incident]" /></section></template>
  <section v-if="tab === 'audit' && data" class="panel"><div class="panel-heading"><div><h2>操作审计<span class="count-label">{{ audits.length }}</span></h2><p>最近 100 条记录，追踪管理操作与执行时间</p></div><button class="btn btn-small" :disabled="refreshing" @click="reload"><Icon name="refresh" :size="15" />刷新</button></div><div v-if="!audits.length" class="empty-state"><Icon name="list" :size="28" /><h3>暂无操作记录</h3><p>管理操作会自动记录在这里。</p></div><div v-else class="table-scroll"><table><thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>目标</th><th>详情</th></tr></thead><tbody><tr v-for="audit in audits" :key="audit.id"><td class="mono">{{ dateTime(audit.createdAt) }}</td><td><span class="audit-user"><Icon name="shield" :size="14" />{{ audit.username || '系统' }}</span></td><td><span class="subtle-tag">{{ auditLabels[audit.action] || audit.action }}</span></td><td class="mono">{{ audit.target || '—' }}</td><td class="audit-detail">{{ audit.detail || '—' }}</td></tr></tbody></table></div></section>
  <template v-if="system"><div class="system-grid"><section class="panel"><div class="panel-heading"><div><h2>运行信息</h2><p>来自当前后端实例的实际配置</p></div><Icon name="server" /></div><dl class="system-values"><div><dt>应用版本</dt><dd><code>{{ system.version }}</code></dd></div><div><dt>实例启动时间</dt><dd>{{ dateTime(system.startedAt) }}</dd></div><div><dt>本次运行时长</dt><dd>{{ duration(system.startedAt) }}</dd></div><div><dt>数据保留时长</dt><dd>{{ system.retentionDays }} 天</dd></div><div><dt>监控数量上限</dt><dd>{{ system.monitorLimit }} 个</dd></div></dl></section><section class="panel"><div class="panel-heading"><div><h2>允许探测的域名</h2><p>由服务器环境变量维护</p></div><Icon name="shield" /></div><div class="allowed-hosts"><code v-for="host in system.allowedHosts" :key="host"><Icon name="globe" :size="16" />{{ host }}</code><p v-if="!system.allowedHosts?.length" class="text-muted">尚未配置允许探测的域名。</p></div><div class="security-note"><Icon name="lock" :size="17" /><p>新增监控时，目标主机必须在此名单中；解析后的地址还需通过公网校验。修改白名单需要更新部署配置。</p></div></section></div><section class="panel system-note"><Icon name="info" :size="22" /><div><h3>关于运行时长与可用率</h3><p>本次运行时长从当前后端进程启动开始计算。服务可用率来自持久化的探测历史，与进程重启时间无关。</p></div></section></template>
  <MonitorForm v-if="showForm" :monitor="editMonitor" @close="showForm = false" @saved="afterSaved" /><IncidentUpdateForm v-if="updateIncident" :incident="updateIncident" @close="updateIncident = null" @saved="afterSaved" />
</template>
