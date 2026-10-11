package com.ldjava.pulseops;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.*;

@Component
class MonitorScheduler implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);
    private final MonitorService monitors;
    private final SafeHttpProbe probe;
    private final PulseProperties properties;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,
            new SynchronousQueue<>(),r -> { Thread t = new Thread(r,"pulse-probe"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    MonitorScheduler(MonitorService monitors, SafeHttpProbe probe, PulseProperties properties) { this.monitors = monitors; this.probe = probe; this.properties = properties; }
    @Override public void run(ApplicationArguments args) {
        if (!properties.seedTargets().isBlank() && monitors.list(true).isEmpty()) {
            for (String target : properties.seedTargets().split(",")) {
                target = target.strip(); if (target.isBlank()) continue;
                URI uri = probe.validate(target);
                monitors.create(new MonitorInput(uri.getHost(),"公开服务连通性巡检",target,60,5000,200,true),"system");
            }
        }
    }
    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    void tick() {
        if (!properties.schedulerEnabled()) return;
        for (long id : monitors.due()) {
            if (workers.getActiveCount() >= 4) break;
            String token = UUID.randomUUID().toString(); var monitor = monitors.claim(id,token);
            if (monitor == null) continue;
            try { workers.execute(() -> {
                try { monitors.finish(id,token,probe.check(monitor.url(),monitor.timeoutMs(),monitor.expectedStatus())); }
                catch (Exception e) { log.error("Could not persist check for monitor {}",id,e); }
                finally { monitors.release(id,token); }
            }); } catch (RejectedExecutionException e) { monitors.release(id,token); }
        }
    }
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    void retention() { monitors.prune(); }
    @PreDestroy void stop() {
        workers.shutdown();
        try { if (!workers.awaitTermination(12,TimeUnit.SECONDS)) workers.shutdownNow(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); workers.shutdownNow(); }
    }
}
