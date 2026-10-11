package com.ldjava.pulseops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

final class RequestRateLimitFilter extends OncePerRequestFilter {
    private record Bucket(long minute, int count) {}
    private final ConcurrentHashMap<String, Bucket> counters = new ConcurrentHashMap<>();
    private final java.util.Set<String> trustedProxies;
    RequestRateLimitFilter() { this(java.util.List.of("127.0.0.1","::1","0:0:0:0:0:0:0:1")); }
    RequestRateLimitFilter(java.util.List<String> trustedProxies) { this.trustedProxies = java.util.Set.copyOf(trustedProxies); }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean login = request.getRequestURI().equals("/api/auth/login");
        // The reverse proxy must overwrite X-Real-IP. Only explicitly configured peer IPs are trusted.
        String remote = request.getRemoteAddr();
        if (trustedProxies.contains(remote)
                && request.getHeader("X-Real-IP") != null && request.getHeader("X-Real-IP").matches("[a-fA-F0-9:.]{3,45}"))
            remote = request.getHeader("X-Real-IP");
        long minute = System.currentTimeMillis() / 60000;
        if (counters.size() > 10000) counters.entrySet().removeIf(e -> e.getValue().minute < minute);
        String key = (login ? "login:" : "api:") + remote;
        if (counters.size() > 12000 && !counters.containsKey(key)) { reject(response); return; }
        Bucket bucket = counters.compute(key, (k, b) -> b == null || b.minute != minute ? new Bucket(minute, 1) : new Bucket(minute, b.count + 1));
        if (bucket.count > (login ? 8 : 300)) { reject(response); return; }
        chain.doFilter(request, response);
    }
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429); response.setHeader("Retry-After", "60");
        response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"message\":\"请求过于频繁，请稍后重试\"}");
    }
}
