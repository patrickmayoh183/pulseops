package com.ldjava.pulseops;

import jakarta.validation.constraints.*;

public record MonitorInput(@NotBlank @Size(max = 80) String name,
                           @Size(max = 500) String description,
                           @NotBlank @Size(max = 2048) String url,
                           @Min(30) @Max(3600) Integer intervalSeconds,
                           @Min(500) @Max(10000) Integer timeoutMs,
                           @Min(100) @Max(599) Integer expectedStatus,
                           Boolean enabled) {
    int interval() { return intervalSeconds == null ? 60 : intervalSeconds; }
    int timeout() { return timeoutMs == null ? 5000 : timeoutMs; }
    int expected() { return expectedStatus == null ? 200 : expectedStatus; }
    boolean active() { return enabled == null || enabled; }
    String descriptionValue() { return description == null ? "" : description.strip(); }
}
