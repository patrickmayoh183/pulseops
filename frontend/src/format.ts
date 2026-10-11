import type { MonitorStatus } from './types'
export const number = (value: number | null | undefined, digits = 0) => value == null ? '—' : new Intl.NumberFormat('zh-CN', { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value)
export const percent = (value: number | null | undefined) => value == null ? '—' : `${number(value, 2)}%`
export const dateTime = (value: string | null | undefined, short = false) => value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', ...(short ? {} : { second: '2-digit' }), hour12: false }) : '等待首次探测'
export const clockTime = (value: string | null | undefined) => value ? new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }) : '—'
export const statusLabel = (value: MonitorStatus | string) => ({ UP: '运行正常', DOWN: '服务异常', PENDING: '等待探测', PAUSED: '已暂停', OPEN: '处理中', RESOLVED: '已恢复' }[value] ?? value)
export function duration(start: string, end?: string | null) {
  const minutes = Math.max(0, Math.floor(((end ? new Date(end).getTime() : Date.now()) - new Date(start).getTime()) / 60000))
  if (minutes < 1) return '不足 1 分钟'
  if (minutes < 60) return `${minutes} 分钟`
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
  return `${Math.floor(minutes / 1440)} 天 ${Math.floor(minutes % 1440 / 60)} 小时`
}
