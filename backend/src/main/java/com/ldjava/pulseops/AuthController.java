package com.ldjava.pulseops;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthenticationManager manager;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;
    private final MonitorService monitors;
    AuthController(AuthenticationManager manager, HttpSessionSecurityContextRepository contexts,
                   HttpSessionCsrfTokenRepository csrf, MonitorService monitors) {
        this.manager = manager; this.contexts = contexts; this.csrf = csrf; this.monitors = monitors;
    }
    record LoginInput(@NotBlank @Size(max = 80) String username, @NotBlank @Size(max = 200) String password) {}
    @GetMapping("/session")
    Map<String, Object> session(Authentication auth, HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return shape(auth, token.getToken());
    }
    @PostMapping("/login")
    Map<String, Object> login(@Valid @RequestBody LoginInput input, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try { auth = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.username(), input.password())); }
        catch (AuthenticationException e) {
            monitors.audit("LOGIN_FAILED", "session", input.username(), "登录验证失败");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确");
        }
        request.getSession(); request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
        SecurityContextHolder.setContext(context); contexts.saveContext(context, request, response);
        CsrfToken token = csrf.generateToken(request); csrf.saveToken(token, request, response);
        monitors.audit("LOGIN", "session", auth.getName(), "管理员登录");
        return shape(auth, token.getToken());
    }
    @PostMapping("/logout")
    Map<String, Object> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        if (auth != null) monitors.audit("LOGOUT", "session", auth.getName(), "管理员退出");
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        var token = csrf.generateToken(request); csrf.saveToken(token, request, response);
        return shape(null, token.getToken());
    }
    private Map<String, Object> shape(Authentication auth, String token) {
        Map<String, Object> map = new LinkedHashMap<>();
        boolean authenticated = auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        map.put("authenticated", authenticated); map.put("username", authenticated ? auth.getName() : null); map.put("csrfToken", token); return map;
    }
}
