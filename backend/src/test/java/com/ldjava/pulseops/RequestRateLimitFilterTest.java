package com.ldjava.pulseops;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimitFilterTest {
    @Test void loginBurstIsLimitedAndUntrustedForwardedHeaderCannotBypassIt() throws Exception {
        var filter = new RequestRateLimitFilter(); var passed = new AtomicInteger();
        for (int i=0; i<9; i++) {
            var request = new MockHttpServletRequest("POST","/api/auth/login");
            request.setRemoteAddr("203.0.113.2"); request.addHeader("X-Real-IP","198.51.100."+i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request,response,(a,b) -> passed.incrementAndGet());
            if (i==8) { assertThat(response.getStatus()).isEqualTo(429); assertThat(response.getHeader("Retry-After")).isEqualTo("60"); }
        }
        assertThat(passed.get()).isEqualTo(8);
    }
    @Test void explicitlyTrustedProxyKeepsDifferentVisitorsInDifferentBuckets() throws Exception {
        var filter = new RequestRateLimitFilter(java.util.List.of("172.19.0.2")); var passed = new AtomicInteger();
        for (int i=0; i<9; i++) {
            var request = new MockHttpServletRequest("POST","/api/auth/login");
            request.setRemoteAddr("172.19.0.2"); request.addHeader("X-Real-IP","198.51.100."+i);
            var response = new MockHttpServletResponse(); filter.doFilter(request,response,(a,b) -> passed.incrementAndGet());
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(passed.get()).isEqualTo(9);
    }
}
