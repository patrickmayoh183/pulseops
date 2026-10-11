package com.ldjava.pulseops;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:pulsetest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.url=jdbc:h2:mem:pulsetest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.flyway.user=sa",
        "pulse.admin-password=TestPassword-Only-123!","pulse.admin-username=admin","pulse.allowed-hosts=example.com,www.aliyun.com",
        "pulse.scheduler-enabled=false","pulse.seed-targets=","server.servlet.session.cookie.secure=false","spring.datasource.hikari.maximum-pool-size=1"})
@AutoConfigureMockMvc
class PulseOpsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired MonitorService monitors;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clear() {
        jdbc.update("DELETE FROM incident_updates"); jdbc.update("DELETE FROM incidents"); jdbc.update("DELETE FROM monitor_checks"); jdbc.update("DELETE FROM monitors"); jdbc.update("DELETE FROM audit_log");
    }
    private long createMonitor() { return ((Number)monitors.create(new MonitorInput("Example","测试服务","https://example.com/?secret=hidden",30,500,200,true),"test").get("id")).longValue(); }
    @Test void publicOverviewHasRealEmptyStateAndAdminRequiresLogin() throws Exception {
        mvc.perform(get("/api/public/overview")).andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus").value("PENDING"))
                .andExpect(jsonPath("$.stats.monitorCount").value(0)).andExpect(jsonPath("$.stats.uptime24h").doesNotExist());
        mvc.perform(get("/api/admin/monitors")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
    }
    @Test void knownSpaRoutesForwardButMissingAssetReturnsNotFound() throws Exception {
        for (String route : new String[]{"/", "/incidents", "/about", "/login", "/admin", "/admin/monitors", "/admin/incidents", "/admin/audit", "/admin/system", "/monitors/12"})
            mvc.perform(get(route)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/assets/missing-file.js")).andExpect(status().isNotFound());
    }
    @Test void sessionCsrfLoginCrudAndLogoutFlow() throws Exception {
        var init = mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false)).andReturn();
        var session = (MockHttpSession)init.getRequest().getSession(false);
        String csrf = mapper.readTree(init.getResponse().getContentAsString()).get("csrfToken").asText();
        String credentials = "{\"username\":\"admin\",\"password\":\"TestPassword-Only-123!\"}";
        mvc.perform(post("/api/auth/login").session(session).contentType("application/json").content(credentials)).andExpect(status().isForbidden());
        var login = mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content(credentials))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andReturn();
        csrf = mapper.readTree(login.getResponse().getContentAsString()).get("csrfToken").asText();
        mvc.perform(get("/api/admin/monitors").session(session)).andExpect(status().isOk());
        String monitor = "{\"name\":\"Example\",\"description\":\"Production HTTP check\",\"url\":\"https://example.com/?key=secret\",\"intervalSeconds\":60,\"timeoutMs\":1000,\"expectedStatus\":200,\"enabled\":true}";
        mvc.perform(post("/api/admin/monitors").session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content(monitor.replace("\"intervalSeconds\":60","\"intervalSeconds\":1"))).andExpect(status().isBadRequest());
        var created = mvc.perform(post("/api/admin/monitors").session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content(monitor))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/public/monitors/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://example.com/"));
        mvc.perform(post("/api/admin/monitors/"+id+"/check").session(session).header("X-CSRF-TOKEN",csrf)).andExpect(status().isAccepted());
        mvc.perform(patch("/api/admin/monitors/"+id+"/enabled").session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(get("/api/admin/audit").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("MONITOR_PAUSED"));
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",csrf)).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/admin/monitors")).andExpect(status().isUnauthorized());
    }
    private void result(long id, String status) {
        monitors.checkSoon(id,"test"); String token = UUID.randomUUID().toString(); assertThat(monitors.claim(id,token)).isNotNull();
        monitors.finish(id,token,new SafeHttpProbe.Result(Instant.now(),status,42,status.equals("UP") ? 200 : 503,status.equals("UP") ? null : "HTTP 503"));
    }
    @Test void twoFailuresOpenOneIncidentAndTwoSuccessesResolveWithMeasuredMetrics() {
        long id = createMonitor();
        result(id,"DOWN"); assertThat(monitors.incidents(20)).isEmpty();
        result(id,"DOWN"); assertThat(monitors.incidents(20)).hasSize(1); assertThat(monitors.incidents(20).getFirst().get("status")).isEqualTo("OPEN");
        result(id,"DOWN"); assertThat(monitors.incidents(20)).hasSize(1);
        result(id,"UP"); assertThat(monitors.incidents(20).getFirst().get("status")).isEqualTo("OPEN");
        result(id,"UP"); assertThat(monitors.incidents(20).getFirst().get("status")).isEqualTo("RESOLVED");
        assertThat(monitors.detail(id).get("uptime24h")).isEqualTo(40.0);
        assertThat(monitors.detail(id).get("avgLatencyMs")).isEqualTo(42L);
        assertThat((java.util.List<?>)monitors.incidents(20).getFirst().get("updates")).hasSize(2);
        assertThat(monitors.overview().get("overallStatus")).isEqualTo("OPERATIONAL");
    }
    @Test void publicIncidentUpdatesCanBeReadWithASingleDatabaseConnection() throws Exception {
        long id = createMonitor(); result(id,"DOWN"); result(id,"DOWN");
        mvc.perform(get("/api/public/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents[0].status").value("OPEN"))
                .andExpect(jsonPath("$.incidents[0].updates[0].message").value("连续 2 次巡检失败，已自动创建事件。"));
    }
    @Test void leasePreventsDuplicateClaimAndStaleCompletionAfterPause() {
        long id = createMonitor(); String token = "first-worker";
        assertThat(monitors.claim(id,token)).isNotNull(); assertThat(monitors.claim(id,"second-worker")).isNull();
        monitors.enabled(id,false,"test");
        monitors.finish(id,token,new SafeHttpProbe.Result(Instant.now(),"UP",30,200,null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_checks",Integer.class)).isZero();
        assertThat(monitors.detail(id).get("status")).isEqualTo("PAUSED");
    }
    @Test void expiredLeaseCanBeReclaimedAndPreviousWorkerCannotFinish() {
        long id = createMonitor(); assertThat(monitors.claim(id,"old-token")).isNotNull();
        jdbc.update("UPDATE monitors SET lease_until=? WHERE id=?",MonitorService.ts(Instant.now().minusSeconds(1)),id);
        assertThat(monitors.claim(id,"new-token")).isNotNull();
        monitors.finish(id,"old-token",new SafeHttpProbe.Result(Instant.now(),"DOWN",100,null,"late"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_checks",Integer.class)).isZero();
        monitors.finish(id,"new-token",new SafeHttpProbe.Result(Instant.now(),"UP",30,200,null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_checks",Integer.class)).isEqualTo(1);
    }
    @Test void retentionRemovesExpiredSamplesFromMetrics() {
        long id = createMonitor();
        jdbc.update("INSERT INTO monitor_checks(monitor_id,checked_at,status,latency_ms,status_code) VALUES (?,?,?,?,?)",id,MonitorService.ts(Instant.now().minusSeconds(31*86400L)),"DOWN",123,503);
        result(id,"UP"); monitors.prune();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_checks",Integer.class)).isEqualTo(1);
        assertThat(monitors.detail(id).get("uptime24h")).isEqualTo(100.0);
    }
    @Test void deniedHostAndInvalidIntervalAreRejected() throws Exception {
        assertThatThrownBy(() -> monitors.create(new MonitorInput("Bad","","https://localhost/",30,500,200,true),"test")).isInstanceOf(ApiException.class);
        mvc.perform(get("/api/public/monitors/99999")).andExpect(status().isNotFound());
    }
}
