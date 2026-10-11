<script setup lang="ts">
import Icon from './Icon.vue'
defineProps<{ message: string; stale?: boolean }>()
defineEmits<{ retry: [] }>()
</script>
<template><div class="error-banner" role="alert"><Icon name="alert" /><div><strong>{{ stale ? '最新状态暂时不可用' : '暂时无法加载' }}</strong><p>{{ message }}{{ stale ? ' 当前展示的是上次成功获取的数据。' : '' }}</p></div><button class="btn btn-small" @click="$emit('retry')"><Icon name="refresh" :size="14" />重试</button></div></template>
