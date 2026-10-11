package com.ldjava.pulseops;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class MonitorStatusTest {
    private MonitorService.Monitor monitor(boolean enabled, Instant lastCheckedAt) {
        return new MonitorService.Monitor(1,"Example","","https://example.com/",enabled,60,5000,200,"UP",0,1,lastCheckedAt,null);
    }
    @Test void staleSuccessIsPendingAndFreshSuccessIsUp() {
        Instant now = Instant.parse("2026-10-09T12:00:00Z");
        assertThat(MonitorService.displayStatus(monitor(true,now.minusSeconds(181)),now)).isEqualTo("PENDING");
        assertThat(MonitorService.displayStatus(monitor(true,now.minusSeconds(180)),now)).isEqualTo("UP");
        assertThat(MonitorService.displayStatus(monitor(true,null),now)).isEqualTo("PENDING");
        assertThat(MonitorService.displayStatus(monitor(false,now.minusSeconds(600)),now)).isEqualTo("PAUSED");
    }
}
