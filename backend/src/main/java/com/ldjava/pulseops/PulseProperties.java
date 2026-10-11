package com.ldjava.pulseops;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "pulse")
public record PulseProperties(String adminUsername, String adminPassword, String allowedHosts,
                              String seedTargets, String icpNumber, int retentionDays,
                              int monitorLimit, boolean schedulerEnabled, String trustedProxies) {
    public List<String> hostList() {
        return Arrays.stream(allowedHosts.split(",")).map(String::trim).filter(s -> !s.isBlank())
                .map(s -> java.net.IDN.toASCII(s).toLowerCase(java.util.Locale.ROOT)).distinct().sorted().toList();
    }
    public List<String> proxyList() {
        return Arrays.stream(trustedProxies.split(",")).map(String::trim).filter(s -> !s.isBlank()).distinct().toList();
    }
}
