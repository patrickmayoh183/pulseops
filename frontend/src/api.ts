import { ref } from 'vue'
import type { Session } from './types'

export const session = ref<Session | null>(null)
let sessionRequest: Promise<Session> | null = null

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); this.name = 'ApiError' }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), 20000)
  try {
    const response = await fetch(`/api${path}`, { credentials: 'same-origin', ...init, signal: controller.signal })
    const contentType = response.headers.get('content-type') ?? ''
    const body = contentType.includes('application/json') ? await response.json() : null
    if (!response.ok) {
      if (response.status === 401) session.value = null
      throw new ApiError(response.status, body?.message || ({ 401: '登录已过期，请重新登录。', 403: '请求未获授权，请刷新页面后重试。', 429: '操作过于频繁，请稍后重试。' }[response.status] ?? `请求失败（${response.status}），请稍后重试。`))
    }
    if (response.status !== 204 && body === null && response.status !== 202) throw new ApiError(502, '服务返回了无法识别的响应，请稍后重试。')
    return body as T
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new Error(error instanceof DOMException && error.name === 'AbortError' ? '请求超时，请检查网络后重试。' : '暂时无法连接服务，请检查网络后重试。')
  } finally { window.clearTimeout(timer) }
}

export function loadSession(force = false): Promise<Session> {
  if (!force && session.value) return Promise.resolve(session.value)
  if (sessionRequest) return sessionRequest
  sessionRequest = request<Session>('/auth/session').then(result => { session.value = result; return result }).finally(() => { sessionRequest = null })
  return sessionRequest
}

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    const auth = await loadSession()
    headers['X-CSRF-TOKEN'] = auth.csrfToken
    if (body !== undefined) headers['Content-Type'] = 'application/json'
  }
  return request<T>(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
}

export const errorMessage = (error: unknown) => error instanceof Error ? error.message : '出现未知错误，请重试。'

export const toast = ref<{ message: string; kind: 'success' | 'error' } | null>(null)
let toastTimer: number | undefined
export function notify(message: string, kind: 'success' | 'error' = 'success') {
  window.clearTimeout(toastTimer)
  toast.value = { message, kind }
  toastTimer = window.setTimeout(() => { toast.value = null }, 5500)
}
