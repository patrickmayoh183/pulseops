package com.ldjava.pulseops;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
class SecurityConfig {
    @Bean
    AuthenticationManager authenticationManager(PulseProperties p) {
        if (p.adminPassword() == null || p.adminPassword().length() < 12)
            throw new IllegalStateException("PULSE_ADMIN_PASSWORD must contain at least 12 characters; no default password is provided");
        if (p.adminUsername() == null || !p.adminUsername().matches("[A-Za-z0-9_.-]{3,80}"))
            throw new IllegalStateException("PULSE_ADMIN_USERNAME must be 3-80 letters, digits, _, . or -");
        if (p.monitorLimit() < 1 || p.monitorLimit() > 100 || p.retentionDays() < 1 || p.retentionDays() > 90)
            throw new IllegalStateException("Monitor limit must be 1-100; retention days must be 1-90");
        var encoder = new BCryptPasswordEncoder(12);
        var users = new InMemoryUserDetailsManager(User.withUsername(p.adminUsername())
                .password(encoder.encode(p.adminPassword())).roles("ADMIN").build());
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean HttpSessionSecurityContextRepository contextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean
    SecurityFilterChain security(HttpSecurity http, HttpSessionCsrfTokenRepository csrf,
                                 HttpSessionSecurityContextRepository contexts, PulseProperties properties) throws Exception {
        http.csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .securityContext(c -> c.securityContextRepository(contexts))
            .authorizeHttpRequests(a -> a.requestMatchers("/api/public/**", "/api/auth/session", "/api/auth/login", "/api/auth/logout", "/actuator/health", "/error").permitAll()
                    .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/favicon.svg", "/logo.svg", "/robots.txt", "/admin", "/admin/**", "/login", "/monitors/*", "/incidents", "/about").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().denyAll())
            .requestCache(c -> c.disable())
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"请先登录\"}");
            }).accessDeniedHandler((req, res, ex) -> {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8"); res.getWriter().write("{\"message\":\"操作被拒绝，请刷新登录状态后重试\"}");
            }))
            .addFilterBefore(new RequestRateLimitFilter(properties.proxyList()), UsernamePasswordAuthenticationFilter.class)
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")));
        return http.build();
    }
}
