<script setup lang="ts">import Icon from '../components/Icon.vue'</script>
<template><div class="not-found"><span class="eyebrow">PAGE NOT FOUND</span><strong>404<span>.</span></strong><h1>这条路径暂时没有信号</h1><p>页面可能不存在，或地址已发生变化。</p><RouterLink to="/" class="btn btn-primary">返回服务总览<Icon name="arrow" :size="16" /></RouterLink></div></template>
