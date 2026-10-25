package com.riotanalizer.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies admin credentials against the configured BCrypt hash.
 *
 * <p>Timing-safe: the username is compared in constant time and a BCrypt check
 * always runs (against a throwaway hash when the username is wrong or admin is
 * not configured), so response time does not reveal which part was wrong.
 */
@Component
public class AdminAccount {

    private static final Logger log = LoggerFactory.getLogger(AdminAccount.class);

    /** $2a$ / $2b$ / $2y$ + 2-digit cost + 53 chars of salt+hash. */
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]?\\$(\\d{2})\\$[./0-9A-Za-z]{53}$");
    private static final int MIN_RECOMMENDED_COST = 10;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String username;
    private final String passwordHash;
    private final String dummyHash;

    public AdminAccount(AdminProperties props) {
        this.username = props.username().trim();
        String hash = props.passwordHash() == null ? "" : props.passwordHash().trim();

        int cost = 12;
        if (hash.isEmpty()) {
            log.warn("ADMIN_PASSWORD_HASH is not set — admin login is DISABLED and tracking cannot be managed.");
            this.passwordHash = null;
        } else {
            Matcher m = BCRYPT.matcher(hash);
            if (!m.matches()) {
                // Fail fast: a malformed hash would silently lock the admin out.
                throw new IllegalStateException(
                        "ADMIN_PASSWORD_HASH is not a valid BCrypt hash (expected $2a$/$2b$/$2y$...). "
                                + "Generate one with `make admin-hash`. If you use docker compose, wrap the value "
                                + "in single quotes in .env so the '$' characters are kept.");
            }
            cost = Integer.parseInt(m.group(1));
            if (cost < MIN_RECOMMENDED_COST) {
                log.warn("ADMIN_PASSWORD_HASH uses BCrypt cost {} — use {} or higher.", cost, MIN_RECOMMENDED_COST);
            }
            this.passwordHash = hash;
            log.info("Admin login enabled for user '{}'.", this.username);
        }
        // Same cost as the real hash so a wrong username takes as long as a wrong password.
        this.dummyHash = new BCryptPasswordEncoder(cost).encode(UUID.randomUUID().toString());
    }

    public boolean isConfigured() {
        return passwordHash != null;
    }

    public String username() {
        return username;
    }

    /** True only when admin is configured and both username and password match. */
    public boolean verify(String candidateUser, String candidatePassword) {
        if (candidateUser == null || candidatePassword == null) {
            return false;
        }
        boolean userOk = MessageDigest.isEqual(
                candidateUser.getBytes(StandardCharsets.UTF_8),
                username.getBytes(StandardCharsets.UTF_8));
        String hash = (isConfigured() && userOk) ? passwordHash : dummyHash;
        boolean passwordOk = encoder.matches(candidatePassword, hash);
        return isConfigured() && userOk && passwordOk;
    }
}
