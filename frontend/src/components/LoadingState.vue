<script setup lang="ts">withDefaults(defineProps<{ label?: string }>(), { label: '正在获取最新状态…' })</script>
<template><div class="loading-state" role="status"><span class="spinner" /><span>{{ label }}</span><div class="skeleton-row" /><div class="skeleton-row short" /></div></template>
