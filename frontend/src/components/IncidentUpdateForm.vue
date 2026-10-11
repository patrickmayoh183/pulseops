<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Incident } from '../types'
import { api, errorMessage, notify } from '../api'
import Icon from './Icon.vue'
const props = defineProps<{ incident: Incident }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const message = ref('')
const submitting = ref(false)
const error = ref('')
onMounted(() => { dialog.value?.showModal() })
function close() { if (!submitting.value) emit('close') }
async function save() {
  if (!message.value.trim() || submitting.value) return
  submitting.value = true; error.value = ''
  try { await api(`/admin/incidents/${props.incident.id}/updates`, 'POST', { message: message.value.trim() }); notify('事件进展已发布到公开状态页'); emit('saved') } catch (e) { error.value = errorMessage(e) } finally { submitting.value = false }
}
</script>
<template><dialog ref="dialog" class="form-dialog" aria-labelledby="incident-form-title" @cancel.prevent="close"><div class="dialog-heading"><div><span class="eyebrow">INCIDENT UPDATE</span><h2 id="incident-form-title">记录事件进展</h2></div><button class="icon-btn" aria-label="关闭弹窗" :disabled="submitting" @click="close"><Icon name="close" /></button></div><form @submit.prevent="save"><div class="dialog-body"><div class="incident-context"><strong>{{ incident.title }}</strong><span>{{ incident.monitorName }}</span></div><label class="field">进展说明<textarea v-model="message" required maxlength="2000" rows="5" placeholder="记录已发现的问题、处理措施或恢复进度…" /><small>提交后将在公开状态页展示，请勿包含账号、密码或其他敏感信息。</small></label><div v-if="error" class="form-error" role="alert">{{ error }}</div></div><div class="dialog-actions"><button type="button" class="btn" :disabled="submitting" @click="close">取消</button><button class="btn btn-primary" :disabled="submitting || !message.trim()"><span v-if="submitting" class="spinner small" />{{ submitting ? '正在发布…' : '发布进展' }}</button></div></form></dialog></template>
