package com.riotanalizer.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single admin account. Only a BCrypt hash of the password is ever
 * configured — the plaintext never lives in config, git or the database.
 *
 * @param username         admin login name (ADMIN_USERNAME)
 * @param passwordHash     BCrypt hash, e.g. "$2y$12$..." (ADMIN_PASSWORD_HASH);
 *                         blank disables admin login entirely
 * @param maxFailedLogins  failed attempts allowed per {@code lockoutMinutes} window
 *                         before login is temporarily blocked
 * @param lockoutMinutes   length of the failed-attempt window / lockout
 * @param cookieSecure     mark session + CSRF cookies Secure (HTTPS only)
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        String username,
        String passwordHash,
        int maxFailedLogins,
        int lockoutMinutes,
        boolean cookieSecure
) {
    public AdminProperties {
        if (username == null || username.isBlank()) {
            username = "admin";
        }
        if (maxFailedLogins <= 0) {
            maxFailedLogins = 10;
        }
        if (lockoutMinutes <= 0) {
            lockoutMinutes = 15;
        }
    }
}
