package com.riotanalizer.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Admin session endpoints. Logout (POST /api/auth/logout) is handled by
 * Spring Security's logout filter, see {@link SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final int MAX_USERNAME = 128;
    private static final int MAX_PASSWORD = 256;

    public record LoginRequest(String username, String password) {
    }

    /** What the SPA needs to decide which controls to show. */
    public record AuthStatusDto(boolean authenticated, String username, boolean loginEnabled) {
    }

    private final AdminAccount adminAccount;
    private final LoginThrottle throttle;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AdminAccount adminAccount, LoginThrottle throttle,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository) {
        this.adminAccount = adminAccount;
        this.throttle = throttle;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/me")
    public AuthStatusDto me() {
        boolean admin = AdminAccess.isAdmin();
        return new AuthStatusDto(admin, admin ? adminAccount.username() : null, adminAccount.isConfigured());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        if (!adminAccount.isConfigured()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Admin login is not configured on this server", request);
        }

        Optional<Duration> wait = throttle.tryAttempt();
        if (wait.isPresent()) {
            long seconds = Math.max(1, (wait.get().toMillis() + 999) / 1000);
            log.warn("Admin login blocked: too many failed attempts (retry in {}s)", seconds);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                    .body(body(HttpStatus.TOO_MANY_REQUESTS,
                            "Too many failed login attempts. Try again in " + humanize(seconds) + ".", request));
        }

        String username = body == null ? null : body.username();
        String password = body == null ? null : body.password();
        boolean ok = username != null && password != null
                && username.length() <= MAX_USERNAME && password.length() <= MAX_PASSWORD
                && adminAccount.verify(username, password);
        if (!ok) {
            log.warn("Failed admin login attempt");
            return error(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
        }
        throttle.recordSuccess();

        // Session fixation: never reuse a session id that existed before login.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                adminAccount.username(), null, AuthorityUtils.createAuthorityList(AdminAccess.AUTHORITY));
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // Rotate the CSRF token so a pre-login token can't be reused.
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);

        log.info("Admin '{}' logged in", adminAccount.username());
        return ResponseEntity.ok(new AuthStatusDto(true, adminAccount.username(), true));
    }

    private static String humanize(long seconds) {
        return seconds < 60 ? seconds + "s" : ((seconds + 59) / 60) + " min";
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(body(status, message, req));
    }

    private static Map<String, Object> body(HttpStatus status, String message, HttpServletRequest req) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("error", message);
        b.put("status", status.value());
        b.put("path", req.getRequestURI());
        b.put("timestamp", Instant.now().toString());
        return b;
    }
}
