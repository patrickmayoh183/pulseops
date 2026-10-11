# PulseOps API contract

Java 21 + Spring Boot + JDBC/Flyway + MySQL, Vue 3 + TypeScript + Vite. API base `/api`. JSON fields camelCase; times ISO 8601 UTC.

Public GET `/api/public/overview`: `{name,description,overallStatus,generatedAt,stats:{monitorCount,operationalCount,uptime24h,avgLatencyMs,openIncidentCount},monitors:[{id,name,description,url,status,enabled,intervalSeconds,uptime24h,avgLatencyMs,lastCheckedAt,history:[{at,status,latencyMs}]}],incidents:[{id,monitorId,monitorName,title,status,startedAt,resolvedAt,updates:[{id,message,createdAt}]}]}`. status: `UP|DOWN|PENDING|PAUSED`; overallStatus: `OPERATIONAL|DEGRADED|OUTAGE|PENDING`. incident status: `OPEN|RESOLVED`. Empty metrics are null and must display as pending/no data, never invented measurements.

GET `/api/public/monitors/{id}` returns monitor object plus `checks:[{id,checkedAt,status,latencyMs,statusCode,error}]` (latest 100). Public URL excludes query/auth; expose name and sanitized origin/path only.

GET `/api/auth/session`: `{authenticated,username,csrfToken}`. POST `/api/auth/login` `{username,password}` returns session shape; POST `/api/auth/logout`. Session HttpOnly cookie. All POST/PUT/PATCH/DELETE require `X-CSRF-TOKEN` from session. Frontend fetches session first and refreshes after login/logout. Auth errors 401, authorization 403. GET public does not require auth.

GET `/api/admin/monitors` returns full monitor array. POST `/api/admin/monitors` and PUT `/api/admin/monitors/{id}` body `{name,description,url,intervalSeconds,timeoutMs,expectedStatus,enabled}`; returns monitor object. Default interval 60s (minimum 30), timeout 5000ms (max 10000), expectedStatus 200. PATCH `/api/admin/monitors/{id}/enabled` `{enabled}`. POST `/api/admin/monitors/{id}/check` schedules immediate check, responds 202. No monitor delete endpoint for MVP.

GET `/api/admin/incidents` list. POST `/api/admin/incidents/{id}/updates` `{message}` adds an update. Incidents open after 2 consecutive failures and resolve after 2 consecutive successes. Public endpoint displays latest 20 incidents and their updates.

GET `/api/admin/audit` latest 100 `{id,action,target,username,createdAt,detail}`. GET `/api/admin/system` `{version,retentionDays,monitorLimit,allowedHosts,startedAt}`. GET `/actuator/health` `{status:UP}`. Health detail restricted.

Error shape `{message}` (additional fields acceptable). No fake production measurements. Initialize 2-3 real allowlisted HTTPS monitors only when configured, empty state otherwise. Host allowlist through `PULSE_ALLOWED_HOSTS`, HTTPS port 443 only; protect SSRF (private/link-local/loopback/metadata, all resolved addresses; no redirects). Backend environment: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PULSE_ADMIN_USERNAME`, `PULSE_ADMIN_PASSWORD`, `PULSE_ALLOWED_HOSTS`, `PULSE_SEED_TARGETS`, `PULSE_SECURE_COOKIE`, `PULSE_ICP_NUMBER`. Credentials must not be committed. Public optional `icpNumber` config field.
