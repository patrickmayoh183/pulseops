package com.ldjava.pulseops;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MonitorService {
    private final JdbcTemplate jdbc;
    private final SafeHttpProbe probe;
    private final PulseProperties properties;
    MonitorService(JdbcTemplate jdbc, SafeHttpProbe probe, PulseProperties properties) { this.jdbc = jdbc; this.probe = probe; this.properties = properties; }
    record Monitor(long id, String name, String description, String url, boolean enabled, int intervalSeconds,
                   int timeoutMs, int expectedStatus, String status, int failureStreak, int successStreak,
                   Instant lastCheckedAt, String leaseToken) {}
    private Monitor row(ResultSet r, int n) throws SQLException {
        return new Monitor(r.getLong("id"), r.getString("name"), r.getString("description"), r.getString("url"), r.getBoolean("enabled"),
                r.getInt("interval_seconds"), r.getInt("timeout_ms"), r.getInt("expected_status"), r.getString("current_status"),
                r.getInt("failure_streak"), r.getInt("success_streak"), instant(r,"last_checked_at"), r.getString("lease_token"));
    }
    static Instant instant(ResultSet r, String column) throws SQLException { var ts = r.getTimestamp(column); return ts == null ? null : ts.toInstant(); }
    static Timestamp ts(Instant value) { return Timestamp.from(value); }
    static Map<String, Object> map(Object... values) {
        var map = new LinkedHashMap<String,Object>(); for (int i = 0; i < values.length; i += 2) map.put((String)values[i], values[i+1]); return map;
    }
    Monitor find(long id) {
        return jdbc.query("SELECT * FROM monitors WHERE id=?", this::row, id).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public List<Map<String,Object>> list(boolean admin) { return jdbc.query("SELECT * FROM monitors ORDER BY id", this::row).stream().map(m -> view(m, admin)).toList(); }
    public Map<String,Object> detail(long id) {
        var result = view(find(id), false);
        result.put("checks", jdbc.query("SELECT * FROM monitor_checks WHERE monitor_id=? ORDER BY checked_at DESC,id DESC LIMIT 100", (r,n) ->
                map("id",r.getLong("id"),"checkedAt",instant(r,"checked_at"),"status",r.getString("status"),"latencyMs",r.getLong("latency_ms"),
                        "statusCode",r.getObject("status_code"),"error",r.getString("error")), id));
        return result;
    }
    private Map<String,Object> view(Monitor m, boolean admin) {
        var metrics = metrics(m.id());
        List<Map<String,Object>> history = new ArrayList<>(jdbc.query("SELECT checked_at,status,latency_ms FROM monitor_checks WHERE monitor_id=? ORDER BY checked_at DESC,id DESC LIMIT 90", (r,n) ->
                map("at",instant(r,"checked_at"),"status",r.getString("status"),"latencyMs",r.getLong("latency_ms")),m.id()));
        Collections.reverse(history);
        var result = map("id",m.id(),"name",m.name(),"description",m.description(),"url",admin ? m.url() : SafeHttpProbe.publicUrl(m.url()),
                "status",displayStatus(m,Instant.now()),"enabled",m.enabled(),"intervalSeconds",m.intervalSeconds(),
                "uptime24h",metrics.get("uptime24h"),"avgLatencyMs",metrics.get("avgLatencyMs"),"lastCheckedAt",m.lastCheckedAt(),"history",history);
        if (admin) { result.put("timeoutMs",m.timeoutMs()); result.put("expectedStatus",m.expectedStatus()); }
        return result;
    }
    static String displayStatus(Monitor monitor, Instant now) {
        if (!monitor.enabled()) return "PAUSED";
        long freshForMs = Math.max(monitor.intervalSeconds()*3000L,monitor.intervalSeconds()*1000L+monitor.timeoutMs()+30000L);
        if (monitor.lastCheckedAt() == null || monitor.lastCheckedAt().isBefore(now.minusMillis(freshForMs))) return "PENDING";
        return monitor.status();
    }
    private Map<String,Object> metrics(Long monitorId) {
        String where = monitorId == null ? "checked_at>=?" : "checked_at>=? AND monitor_id=?";
        Object[] args = monitorId == null ? new Object[]{ts(Instant.now().minus(24,ChronoUnit.HOURS))} : new Object[]{ts(Instant.now().minus(24,ChronoUnit.HOURS)),monitorId};
        return jdbc.queryForObject("SELECT COUNT(*) AS total,SUM(CASE WHEN status='UP' THEN 1 ELSE 0 END) AS passed,AVG(latency_ms) AS latency FROM monitor_checks WHERE " + where,
                (r,n) -> { long count = r.getLong("total"); return map("uptime24h",count == 0 ? null : Math.round(r.getLong("passed") * 10000.0 / count) / 100.0,
                        "avgLatencyMs",count == 0 ? null : Math.round(r.getDouble("latency"))); },args);
    }
    public Map<String,Object> overview() {
        var monitors = list(false); var metrics = metrics(null);
        long active = monitors.stream().filter(m -> Boolean.TRUE.equals(m.get("enabled"))).count();
        long up = monitors.stream().filter(m -> "UP".equals(m.get("status"))).count();
        long down = monitors.stream().filter(m -> "DOWN".equals(m.get("status"))).count();
        String overall = active == 0 || (up == 0 && down == 0) ? "PENDING" : down == active ? "OUTAGE" : down > 0 ? "DEGRADED" : up == active ? "OPERATIONAL" : "PENDING";
        return map("name","PulseOps","description","持续观察每一次响应，让服务状态清晰可见。","overallStatus",overall,"generatedAt",Instant.now(),
                "icpNumber",properties.icpNumber(),"stats",map("monitorCount",monitors.size(),"operationalCount",up,"uptime24h",metrics.get("uptime24h"),
                        "avgLatencyMs",metrics.get("avgLatencyMs"),"openIncidentCount",jdbc.queryForObject("SELECT COUNT(*) FROM incidents WHERE status='OPEN'",Long.class)),
                "monitors",monitors,"incidents",incidents(20));
    }
    @Transactional
    public Map<String,Object> create(MonitorInput input, String username) {
        probe.validate(input.url());
        jdbc.queryForObject("SELECT name FROM application_locks WHERE name='monitor-create' FOR UPDATE",String.class);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM monitors",Integer.class) >= properties.monitorLimit()) throw ApiException.badRequest("已达到监控数量上限");
        Instant now = Instant.now();
        long id = insert("INSERT INTO monitors(name,description,url,enabled,interval_seconds,timeout_ms,expected_status,current_status,next_check_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',?,?,?)",
                input.name().strip(),input.descriptionValue(),input.url().strip(),input.active(),input.interval(),input.timeout(),input.expected(),ts(now),ts(now),ts(now));
        audit("MONITOR_CREATED", "monitor:"+id,username,input.name().strip());
        return view(find(id),true);
    }
    @Transactional
    public Map<String,Object> update(long id, MonitorInput input, String username) {
        probe.validate(input.url()); find(id);
        jdbc.update("UPDATE monitors SET name=?,description=?,url=?,enabled=?,interval_seconds=?,timeout_ms=?,expected_status=?,current_status='PENDING',failure_streak=0,success_streak=0,lease_token=NULL,lease_until=NULL,next_check_at=?,updated_at=? WHERE id=?",
                input.name().strip(),input.descriptionValue(),input.url().strip(),input.active(),input.interval(),input.timeout(),input.expected(),ts(Instant.now()),ts(Instant.now()),id);
        audit("MONITOR_UPDATED","monitor:"+id,username,input.name().strip()); return view(find(id),true);
    }
    @Transactional
    public Map<String,Object> enabled(long id, boolean enabled, String username) {
        find(id); jdbc.update("UPDATE monitors SET enabled=?,current_status='PENDING',failure_streak=0,success_streak=0,lease_token=NULL,lease_until=NULL,next_check_at=?,updated_at=? WHERE id=?",enabled,ts(Instant.now()),ts(Instant.now()),id);
        audit(enabled ? "MONITOR_RESUMED" : "MONITOR_PAUSED","monitor:"+id,username,enabled ? "恢复巡检" : "暂停巡检"); return view(find(id),true);
    }
    @Transactional
    public void checkSoon(long id, String username) {
        Monitor monitor = find(id); if (!monitor.enabled()) throw ApiException.badRequest("请先启用监控");
        jdbc.update("UPDATE monitors SET next_check_at=? WHERE id=?",ts(Instant.now()),id);
        audit("CHECK_REQUESTED","monitor:"+id,username,"请求立即巡检");
    }
    public List<Map<String,Object>> incidents(int limit) {
        List<Map<String,Object>> incidents = jdbc.query("SELECT i.*,m.name AS monitor_name FROM incidents i JOIN monitors m ON m.id=i.monitor_id ORDER BY i.started_at DESC,i.id DESC LIMIT ?", (r,n) -> {
            long id = r.getLong("id");
            return map("id",id,"monitorId",r.getLong("monitor_id"),"monitorName",r.getString("monitor_name"),"title",r.getString("title"),"status",r.getString("status"),
                    "startedAt",instant(r,"started_at"),"resolvedAt",instant(r,"resolved_at"));
        },limit);
        // Materialize the outer query before querying updates: a public request must never hold
        // one pool connection while waiting for a second connection in a nested RowMapper.
        for (var incident : incidents) incident.put("updates",jdbc.query("SELECT * FROM incident_updates WHERE incident_id=? ORDER BY created_at,id",
                (u,x) -> map("id",u.getLong("id"),"message",u.getString("message"),"createdAt",instant(u,"created_at")),incident.get("id")));
        return incidents;
    }
    @Transactional
    public Map<String,Object> incidentUpdate(long id, String message, String username) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM incidents WHERE id=?",Integer.class,id) == 0) throw ApiException.notFound();
        Instant now = Instant.now(); long updateId = insert("INSERT INTO incident_updates(incident_id,message,created_at) VALUES (?,?,?)",id,message.strip(),ts(now));
        audit("INCIDENT_UPDATED","incident:"+id,username,"新增事件进展"); return map("id",updateId,"message",message.strip(),"createdAt",now);
    }
    public void audit(String action, String target, String username, String detail) {
        jdbc.update("INSERT INTO audit_log(action,target,username,created_at,detail) VALUES (?,?,?,?,?)",action,target,username,ts(Instant.now()),detail);
    }
    public List<Map<String,Object>> auditList() {
        return jdbc.query("SELECT * FROM audit_log ORDER BY created_at DESC,id DESC LIMIT 100",(r,n) -> map("id",r.getLong("id"),"action",r.getString("action"),"target",r.getString("target"),"username",r.getString("username"),"createdAt",instant(r,"created_at"),"detail",r.getString("detail")));
    }
    public List<Long> due() {
        Instant now = Instant.now(); return jdbc.queryForList("SELECT id FROM monitors WHERE enabled=TRUE AND next_check_at<=? AND (lease_until IS NULL OR lease_until<?) ORDER BY next_check_at LIMIT 16",Long.class,ts(now),ts(now));
    }
    public Monitor claim(long id, String token) {
        Instant now = Instant.now();
        int changed = jdbc.update("UPDATE monitors SET lease_token=?,lease_until=? WHERE id=? AND enabled=TRUE AND next_check_at<=? AND (lease_until IS NULL OR lease_until<?)",token,ts(now.plusSeconds(45)),id,ts(now),ts(now));
        return changed == 1 ? find(id) : null;
    }
    public void release(long id, String token) { jdbc.update("UPDATE monitors SET lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?",id,token); }
    @Transactional
    public void finish(long id, String token, SafeHttpProbe.Result result) {
        Monitor m = jdbc.query("SELECT * FROM monitors WHERE id=? FOR UPDATE",this::row,id).stream().findFirst().orElse(null);
        if (m == null || !m.enabled() || !Objects.equals(token,m.leaseToken())) return;
        boolean up = result.status().equals("UP"); int failures = up ? 0 : Math.min(m.failureStreak()+1,1000000); int successes = up ? Math.min(m.successStreak()+1,1000000) : 0;
        jdbc.update("INSERT INTO monitor_checks(monitor_id,checked_at,status,latency_ms,status_code,error) VALUES (?,?,?,?,?,?)",id,ts(result.checkedAt()),result.status(),result.latencyMs(),result.statusCode(),result.error());
        var open = jdbc.queryForList("SELECT id FROM incidents WHERE monitor_id=? AND status='OPEN'",Long.class,id);
        if (failures >= 2 && open.isEmpty()) {
            long incident = insert("INSERT INTO incidents(monitor_id,title,status,started_at) VALUES (?,?,'OPEN',?)",id,m.name()+" 服务异常",ts(result.checkedAt()));
            insert("INSERT INTO incident_updates(incident_id,message,created_at) VALUES (?,?,?)",incident,"连续 2 次巡检失败，已自动创建事件。",ts(result.checkedAt()));
            audit("INCIDENT_OPENED","incident:"+incident,"system","连续巡检失败");
        }
        if (successes >= 2 && !open.isEmpty()) for (long incident : open) {
            jdbc.update("UPDATE incidents SET status='RESOLVED',resolved_at=? WHERE id=?",ts(result.checkedAt()),incident);
            insert("INSERT INTO incident_updates(incident_id,message,created_at) VALUES (?,?,?)",incident,"连续 2 次巡检成功，服务已恢复。",ts(result.checkedAt()));
            audit("INCIDENT_RESOLVED","incident:"+incident,"system","连续巡检成功");
        }
        jdbc.update("UPDATE monitors SET current_status=?,failure_streak=?,success_streak=?,last_checked_at=?,next_check_at=?,lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?",
                result.status(),failures,successes,ts(result.checkedAt()),ts(Instant.now().plusSeconds(m.intervalSeconds())),id,token);
    }
    public void prune() {
        Timestamp cutoff = ts(Instant.now().minus(properties.retentionDays(),ChronoUnit.DAYS));
        jdbc.update("DELETE FROM monitor_checks WHERE checked_at<?",cutoff);
        jdbc.update("DELETE FROM audit_log WHERE created_at<?",ts(Instant.now().minus(90,ChronoUnit.DAYS)));
    }
    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> { PreparedStatement statement = connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for (int i = 0; i < args.length; i++) statement.setObject(i+1,args[i]); return statement; },keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
