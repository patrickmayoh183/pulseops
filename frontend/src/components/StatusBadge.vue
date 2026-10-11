<script setup lang="ts">
import { statusLabel } from '../format'
defineProps<{ status: string; label?: string }>()
</script>
<template><span class="status-badge" :class="status.toLowerCase()"><span class="status-dot" />{{ label || statusLabel(status) }}</span></template>
