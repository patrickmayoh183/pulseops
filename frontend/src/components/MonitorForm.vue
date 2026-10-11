<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage, notify } from '../api'
import type { Monitor, MonitorInput } from '../types'
import Icon from './Icon.vue'
const props = defineProps<{ monitor: Monitor | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const submitting = ref(false)
const error = ref('')
const form = ref<MonitorInput>({ name: props.monitor?.name || '', description: props.monitor?.description || '', url: props.monitor?.url || '', intervalSeconds: props.monitor?.intervalSeconds ?? 60, timeoutMs: props.monitor?.timeoutMs ?? 5000, expectedStatus: props.monitor?.expectedStatus ?? 200, enabled: props.monitor?.enabled ?? true })
onMounted(() => { dialog.value?.showModal() })
function close() { if (!submitting.value) emit('close') }
async function save() {
  if (submitting.value) return
  error.value = ''
  try {
    const url = new URL(form.value.url.trim())
    if (url.protocol !== 'https:' || (url.port && url.port !== '443') || url.username || url.password || url.hash) {
      error.value = '仅支持 HTTPS 443 端口，地址不得包含用户名、密码或 # 片段。'
      return
    }
  } catch { error.value = '请输入有效的完整 HTTPS 地址。'; return }
  submitting.value = true
  try {
    await api(props.monitor ? `/admin/monitors/${props.monitor.id}` : '/admin/monitors', props.monitor ? 'PUT' : 'POST', { ...form.value, name: form.value.name.trim(), description: form.value.description.trim(), url: form.value.url.trim() })
    notify(props.monitor ? '监控配置已更新' : '监控已创建，正在等待首次探测')
    emit('saved')
  } catch (e) { error.value = errorMessage(e) } finally { submitting.value = false }
}
</script>
<template>
  <dialog ref="dialog" class="form-dialog" aria-labelledby="monitor-form-title" @cancel.prevent="close" @click="event => { if (event.target === dialog) close() }"><div class="dialog-heading"><div><span class="eyebrow">MONITOR CONFIGURATION</span><h2 id="monitor-form-title">{{ monitor ? '编辑监控服务' : '添加监控服务' }}</h2></div><button class="icon-btn" aria-label="关闭弹窗" :disabled="submitting" @click="close"><Icon name="close" /></button></div><form @submit.prevent="save"><div class="dialog-body"><label class="field">服务名称<span class="required">*</span><input v-model="form.name" required maxlength="80" placeholder="例如：官方网站" autocomplete="off" /></label><label class="field">服务描述<input v-model="form.description" maxlength="500" placeholder="简要说明此服务的用途" /></label><label class="field">监控地址<span class="required">*</span><input v-model="form.url" type="url" required maxlength="2048" placeholder="https://example.com/health" /><small>仅支持 HTTPS 443 端口，目标域名须位于服务器白名单中。禁止私网、回环及云元数据地址，不跟随重定向。</small></label><div class="form-grid"><label class="field">探测间隔（秒）<input v-model.number="form.intervalSeconds" type="number" required min="30" max="3600" step="1" /><small>30–3,600 秒</small></label><label class="field">请求超时（毫秒）<input v-model.number="form.timeoutMs" type="number" required min="500" max="10000" step="1" /><small>500–10,000 毫秒</small></label></div><label class="field">预期 HTTP 状态码<input v-model.number="form.expectedStatus" type="number" required min="100" max="599" step="1" /><small>实际响应需与该状态码一致，才判为成功</small></label><label class="checkbox-field"><input v-model="form.enabled" type="checkbox" /><span>启用监控<small>按设定间隔自动发起探测</small></span></label><div v-if="error" class="form-error" role="alert">{{ error }}</div></div><div class="dialog-actions"><button type="button" class="btn" :disabled="submitting" @click="close">取消</button><button type="submit" class="btn btn-primary" :disabled="submitting"><span v-if="submitting" class="spinner small" />{{ submitting ? '正在保存…' : '保存配置' }}</button></div></form></dialog>
</template>
