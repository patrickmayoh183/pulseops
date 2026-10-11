package com.ldjava.pulseops;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
class ApiController {
    private final MonitorService monitors;
    private final PulseProperties properties;
    private final Instant startedAt = Instant.now();
    ApiController(MonitorService monitors, PulseProperties properties) { this.monitors = monitors; this.properties = properties; }
    @GetMapping("/api/public/overview") Map<String,Object> overview() { return monitors.overview(); }
    @GetMapping("/api/public/monitors/{id}") Map<String,Object> detail(@PathVariable long id) { return monitors.detail(id); }
    @GetMapping("/api/admin/monitors") List<Map<String,Object>> list() { return monitors.list(true); }
    @PostMapping("/api/admin/monitors") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> create(@Valid @RequestBody MonitorInput input, Authentication auth) { return monitors.create(input,auth.getName()); }
    @PutMapping("/api/admin/monitors/{id}")
    Map<String,Object> update(@PathVariable long id, @Valid @RequestBody MonitorInput input, Authentication auth) { return monitors.update(id,input,auth.getName()); }
    record EnabledInput(@NotNull Boolean enabled) {}
    @PatchMapping("/api/admin/monitors/{id}/enabled")
    Map<String,Object> enabled(@PathVariable long id, @Valid @RequestBody EnabledInput input, Authentication auth) { return monitors.enabled(id,input.enabled(),auth.getName()); }
    @PostMapping("/api/admin/monitors/{id}/check") @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String,Object> check(@PathVariable long id, Authentication auth) { monitors.checkSoon(id,auth.getName()); return Map.of("message","已加入巡检队列"); }
    @GetMapping("/api/admin/incidents") List<Map<String,Object>> incidents() { return monitors.incidents(100); }
    record UpdateInput(@NotBlank @Size(max = 2000) String message) {}
    @PostMapping("/api/admin/incidents/{id}/updates") @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> addUpdate(@PathVariable long id, @Valid @RequestBody UpdateInput input, Authentication auth) { return monitors.incidentUpdate(id,input.message(),auth.getName()); }
    @GetMapping("/api/admin/audit") List<Map<String,Object>> audit() { return monitors.auditList(); }
    @GetMapping("/api/admin/system") Map<String,Object> system() {
        return MonitorService.map("version","1.0.0","retentionDays",properties.retentionDays(),"monitorLimit",properties.monitorLimit(),"allowedHosts",properties.hostList(),"startedAt",startedAt);
    }
}
