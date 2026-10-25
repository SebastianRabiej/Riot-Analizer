package com.riotanalizer.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

/**
 * Security model: the app is public (read-only stats) except for everything
 * related to tracking, which requires the single admin.
 *
 * <ul>
 *   <li>Login: POST /api/auth/login checks a BCrypt hash, then stores the
 *       authentication in a server-side session. The session cookie is
 *       HttpOnly + SameSite=Strict (+ Secure in production) and its id is
 *       rotated on login (session-fixation protection).</li>
 *   <li>CSRF: double-submit cookie (XSRF-TOKEN, readable by the SPA) that must
 *       be echoed in X-XSRF-TOKEN on every POST/PUT/DELETE — including login
 *       and logout. The token is rotated on login.</li>
 *   <li>Admin-only endpoints are enforced twice: URL rules here and
 *       {@code @PreAuthorize} on the controller methods.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AdminProperties.class)
public class SecurityConfig {

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(AdminProperties props) {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(c -> c.secure(props.cookieSecure()).sameSite("Strict").path("/"));
        return repo;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // Don't create sessions for anonymous visitors just to remember a 401'd URL.
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/players/*/*/track").hasRole(AdminAccess.ROLE)
                        .requestMatchers(HttpMethod.DELETE, "/api/players/*/*/track").hasRole(AdminAccess.ROLE)
                        .requestMatchers("/api/tracked", "/api/tracked/**").hasRole(AdminAccess.ROLE)
                        .requestMatchers("/api/discovered", "/api/discovered/**").hasRole(AdminAccess.ROLE)
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                JsonErrors.write(req, res, 401, "Admin login required"))
                        .accessDeniedHandler((req, res, e) ->
                                JsonErrors.write(req, res, 403, e instanceof CsrfException
                                        ? "Invalid or missing CSRF token - reload the page and try again"
                                        : "Forbidden")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
                        .deleteCookies(sessionCookieName));
        return http.build();
    }
}
