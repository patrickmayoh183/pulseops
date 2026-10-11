export type MonitorStatus = 'UP' | 'DOWN' | 'PENDING' | 'PAUSED'
export interface HistoryPoint { at: string; status: MonitorStatus; latencyMs: number | null }
export interface Monitor {
  id: number; name: string; description: string; url: string; status: MonitorStatus;
  enabled: boolean; intervalSeconds: number; uptime24h: number | null; avgLatencyMs: number | null;
  lastCheckedAt: string | null; history: HistoryPoint[];
  timeoutMs?: number; expectedStatus?: number;
}
export interface Check {
  id: number; checkedAt: string; status: MonitorStatus; latencyMs: number | null;
  statusCode: number | null; error: string | null;
}
export interface MonitorDetail extends Monitor { checks: Check[] }
export interface IncidentUpdate { id: number; message: string; createdAt: string }
export interface Incident {
  id: number; monitorId: number; monitorName: string; title: string; status: 'OPEN' | 'RESOLVED';
  startedAt: string; resolvedAt: string | null; updates: IncidentUpdate[];
}
export interface Overview {
  name: string; description: string; overallStatus: 'OPERATIONAL' | 'DEGRADED' | 'OUTAGE' | 'PENDING';
  generatedAt: string; icpNumber?: string;
  stats: { monitorCount: number; operationalCount: number; uptime24h: number | null; avgLatencyMs: number | null; openIncidentCount: number };
  monitors: Monitor[]; incidents: Incident[];
}
export interface Session { authenticated: boolean; username: string | null; csrfToken: string }
export interface Audit { id: number; action: string; target: string; username: string; createdAt: string; detail: string }
export interface SystemInfo { version: string; retentionDays: number; monitorLimit: number; allowedHosts: string[]; startedAt: string }
export interface MonitorInput {
  name: string; description: string; url: string; intervalSeconds: number; timeoutMs: number; expectedStatus: number; enabled: boolean;
}
