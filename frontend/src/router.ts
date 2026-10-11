import { createRouter, createWebHistory } from 'vue-router'
import { loadSession } from './api'
import OverviewView from './views/OverviewView.vue'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (_to, _from, savedPosition) => savedPosition || { top: 0 },
  routes: [
    { path: '/', component: OverviewView, meta: { title: '服务总览' } },
    { path: '/incidents', component: () => import('./views/IncidentsView.vue'), meta: { title: '事件记录' } },
    { path: '/monitors/:id(\\d+)', component: () => import('./views/MonitorView.vue'), meta: { title: '监控详情' } },
    { path: '/about', component: () => import('./views/AboutView.vue'), meta: { title: '工程说明' } },
    { path: '/login', component: () => import('./views/LoginView.vue'), meta: { title: '管理员登录' } },
    { path: '/admin', redirect: '/admin/monitors' },
    { path: '/admin/:tab(monitors|incidents|audit|system)', component: () => import('./views/AdminView.vue'), meta: { title: '管理控制台', requiresAuth: true } },
    { path: '/:pathMatch(.*)*', component: () => import('./views/NotFoundView.vue'), meta: { title: '页面不存在' } },
  ],
})

router.beforeEach(async to => {
  document.title = `${String(to.meta.title || '服务状态')} · PulseOps`
  if (to.meta.requiresAuth) {
    try { if ((await loadSession()).authenticated) return true } catch { /* Login provides a recoverable error state. */ }
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})
