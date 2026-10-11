<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, loadSession, session, toast, notify, errorMessage } from './api'
import Icon from './components/Icon.vue'
const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const loggingOut = ref(false)
const icpNumber = ref('')
watch(() => route.fullPath, () => { mobileOpen.value = false })
onMounted(() => { void loadSession().catch(() => {}); void api<{ icpNumber?: string }>('/public/overview').then(r => { icpNumber.value = r.icpNumber || '' }).catch(() => {}) })
async function logout() {
  loggingOut.value = true
  try { await api('/auth/logout', 'POST'); session.value = null; await loadSession(true); await router.push('/'); notify('已退出管理控制台') } catch (e) { notify(errorMessage(e), 'error') } finally { loggingOut.value = false }
}
</script>
<template>
  <a class="skip-link" href="#main-content">跳转到主要内容</a>
  <div class="app-shell">
    <aside class="sidebar" :class="{ 'mobile-open': mobileOpen }">
      <RouterLink to="/" class="brand"><span class="brand-mark"><Icon name="pulse" :size="27" /></span><span>Pulse<span class="brand-light">Ops</span><small>SERVICE OBSERVABILITY</small></span></RouterLink>
      <div class="workspace-chip"><span class="workspace-avatar">P</span><span>个人工程实验室<small>Production workspace</small></span><span class="workspace-dot" /></div>
      <span class="nav-heading">工作空间</span>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink to="/" :class="{ active: route.path === '/' || route.path.startsWith('/monitors/') }"><Icon name="grid" /><span>服务总览</span><span class="nav-key">01</span></RouterLink>
        <RouterLink to="/incidents" :class="{ active: route.path === '/incidents' }"><Icon name="bell" /><span>事件记录</span><span class="nav-key">02</span></RouterLink>
        <RouterLink to="/about" :class="{ active: route.path === '/about' }"><Icon name="book" /><span>工程说明</span><span class="nav-key">03</span></RouterLink>
      </nav>
      <div class="sidebar-note"><span class="sidebar-note-icon"><Icon name="pulse" :size="24" /></span><h3>让每一次异常，都有迹可循。</h3><p>持续探测、记录变化，<br />用真实数据理解服务状态。</p><RouterLink to="/about">探索项目设计 <Icon name="arrow" :size="15" /></RouterLink></div>
      <div class="sidebar-bottom"><span class="nav-heading">管理</span><nav class="main-nav" aria-label="管理导航"><RouterLink :to="session?.authenticated ? '/admin' : '/login'" :class="{ active: route.path.startsWith('/admin') || route.path === '/login' }"><Icon name="shield" /><span>管理控制台</span><Icon name="external" :size="14" /></RouterLink></nav><div class="sidebar-version"><span class="mini-dot" />PulseOps <span>v1.0</span></div></div>
    </aside>
    <button v-if="mobileOpen" class="sidebar-backdrop" aria-label="关闭导航" @click="mobileOpen = false" />
    <div class="main-shell">
      <header class="topbar"><div class="breadcrumb"><button class="icon-btn mobile-menu" aria-label="打开导航" :aria-expanded="mobileOpen" @click="mobileOpen = !mobileOpen"><Icon name="menu" /></button><span class="breadcrumb-home">工作空间</span><span class="breadcrumb-sep">/</span><strong>{{ route.meta.title }}</strong></div><div class="topbar-right"><span class="env-badge"><span />正式环境</span><template v-if="session?.authenticated"><RouterLink to="/admin" class="user-chip"><span>{{ session.username?.slice(0, 1).toUpperCase() }}</span>{{ session.username }}</RouterLink><button class="icon-btn" :disabled="loggingOut" title="退出登录" aria-label="退出登录" @click="logout"><Icon name="logout" :size="17" /></button></template><RouterLink v-else to="/login" class="topbar-login">管理员入口<Icon name="arrow" :size="15" /></RouterLink></div></header>
      <main id="main-content" class="page-content"><RouterView :key="route.path" /></main>
      <footer class="footer"><span><Icon name="pulse" :size="15" />PulseOps <span class="footer-sep">/</span> 每个状态，来自真实探测。</span><div><a v-if="icpNumber" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">{{ icpNumber }}</a><RouterLink to="/about">Built with Java & Vue</RouterLink></div></footer>
    </div>
  </div>
  <Transition name="toast"><div v-if="toast" class="toast" :class="toast.kind" role="status"><Icon :name="toast.kind === 'success' ? 'check' : 'alert'" :size="19" /><span>{{ toast.message }}</span><button class="icon-btn" aria-label="关闭提示" @click="toast = null"><Icon name="close" :size="16" /></button></div></Transition>
</template>
