<script setup lang="ts">
import type { Incident } from '../types'
import { dateTime, duration } from '../format'
import StatusBadge from './StatusBadge.vue'
import Icon from './Icon.vue'
withDefaults(defineProps<{ incidents: Incident[]; compact?: boolean }>(), { compact: false })
</script>
<template>
  <div v-if="!incidents.length" class="empty-state incident-empty"><span class="empty-icon"><Icon name="check" :size="27" /></span><h3>暂无故障事件</h3><p>当连续两次探测失败时，系统会自动记录事件。</p></div>
  <div v-else class="incident-list">
    <article v-for="incident in incidents" :key="incident.id" class="incident-item" :class="incident.status.toLowerCase()">
      <span class="timeline-dot"><Icon :name="incident.status === 'OPEN' ? 'alert' : 'check'" :size="15" /></span>
      <div class="incident-content"><div class="incident-heading"><h3>{{ incident.title }}</h3><StatusBadge :status="incident.status" /></div>
        <p class="incident-meta"><RouterLink :to="`/monitors/${incident.monitorId}`">{{ incident.monitorName }}</RouterLink><span>·</span><time :datetime="incident.startedAt">{{ dateTime(incident.startedAt, true) }}</time><span>·</span><span>{{ incident.status === 'OPEN' ? '已持续' : '持续' }} {{ duration(incident.startedAt, incident.resolvedAt) }}</span></p>
        <div v-if="!compact && incident.updates?.length" class="incident-updates"><div v-for="update in [...incident.updates].sort((a,b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())" :key="update.id" class="incident-update"><time :datetime="update.createdAt">{{ dateTime(update.createdAt, true) }}</time><p>{{ update.message }}</p></div></div>
        <p v-if="incident.resolvedAt && !compact" class="resolved-note"><Icon name="check" :size="14" />恢复于 {{ dateTime(incident.resolvedAt) }}</p>
      </div>
    </article>
  </div>
</template>
