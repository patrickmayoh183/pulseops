<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { api, loadSession, session, errorMessage } from '../api'
import Icon from '../components/Icon.vue'
const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const ready = ref(false)
const visible = ref(false)
async function prepare() {
  error.value = ''
  try { await loadSession(true); ready.value = true; if (session.value?.authenticated) await router.replace('/admin/monitors') } catch (e) { error.value = errorMessage(e) }
}
onMounted(prepare)
async function login() {
  if (loading.value) return
  loading.value = true; error.value = ''
  try {
    await api('/auth/login', 'POST', { username: username.value.trim(), password: password.value })
    await loadSession(true)
    password.value = ''
    const target = typeof route.query.redirect === 'string' && /^\/admin(?:\/|$)/.test(route.query.redirect) ? route.query.redirect : '/admin/monitors'
    await router.replace(target)
  } catch (e) { error.value = errorMessage(e); await loadSession(true).catch(() => {}) } finally { loading.value = false }
}
</script>
<template>
  <div class="login-layout"><div class="login-story"><span class="eyebrow">THE CONTROL ROOM</span><h1>可靠的服务，<br />始于每一次<br /><span>细微的观测。</span></h1><p>在这里管理监控目标、记录故障进展，<br />让可用性管理成为日常。</p><div class="login-features"><span><Icon name="activity" :size="18" />持续的 HTTPS 探测</span><span><Icon name="bell" :size="18" />自动化故障生命周期</span><span><Icon name="shield" :size="18" />可追溯的操作记录</span></div><div class="login-orbit" aria-hidden="true"><Icon name="pulse" :size="100" /></div></div><section class="login-card"><span class="login-lock"><Icon name="lock" :size="25" /></span><h2>登录管理控制台</h2><p>使用管理员账号继续</p><form @submit.prevent="login"><label class="field">用户名<input v-model="username" required name="username" autocomplete="username" maxlength="80" placeholder="输入管理员用户名" /></label><label class="field">密码<span class="password-input"><input v-model="password" required name="password" :type="visible ? 'text' : 'password'" autocomplete="current-password" maxlength="200" placeholder="输入密码" /><button type="button" class="icon-btn" :aria-label="visible ? '隐藏密码' : '显示密码'" @click="visible = !visible"><Icon name="eye" :size="18" /></button></span></label><div v-if="error" class="form-error" role="alert">{{ error }}<button v-if="!ready" type="button" class="text-button" @click="prepare">重新连接</button></div><button type="submit" class="btn btn-primary login-submit" :disabled="loading || !ready"><span v-if="loading" class="spinner small" /><span>{{ loading ? '正在登录…' : '进入控制台' }}</span><Icon v-if="!loading" name="arrow" :size="17" /></button></form><div class="login-hint"><Icon name="shield" :size="15" />管理员账号由部署环境配置</div><RouterLink to="/" class="back-link"><Icon name="back" :size="15" />返回公开状态页</RouterLink></section></div>
</template>
