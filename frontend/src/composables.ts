import { onMounted, onUnmounted, ref } from 'vue'
import { api, errorMessage } from './api'

export function useResource<T>(path: () => string, poll = 0) {
  const data = ref<T | null>(null)
  const loading = ref(true)
  const refreshing = ref(false)
  const error = ref('')
  const updatedAt = ref<Date | null>(null)
  let stopped = false
  let timer: number | undefined
  async function reload() {
    if (refreshing.value) return
    refreshing.value = true
    try {
      const result = await api<T>(path())
      if (!stopped) { data.value = result; error.value = ''; updatedAt.value = new Date() }
    } catch (err) { if (!stopped) error.value = errorMessage(err) }
    finally { if (!stopped) { loading.value = false; refreshing.value = false } }
  }
  onMounted(() => {
    void reload()
    if (poll) timer = window.setInterval(() => { if (!document.hidden) void reload() }, poll)
  })
  onUnmounted(() => { stopped = true; window.clearInterval(timer) })
  return { data, loading, refreshing, error, updatedAt, reload }
}
