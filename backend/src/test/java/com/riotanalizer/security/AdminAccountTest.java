package com.riotanalizer.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminAccountTest {

    private static final String PASSWORD = "correct horse battery staple";

    private static AdminAccount account(String hash) {
        return new AdminAccount(new AdminProperties("admin", hash, 10, 15, true));
    }

    @Test
    void acceptsOnlyMatchingUsernameAndPassword() {
        AdminAccount a = account(new BCryptPasswordEncoder(4).encode(PASSWORD));
        assertTrue(a.isConfigured());
        assertTrue(a.verify("admin", PASSWORD));
        assertFalse(a.verify("admin", PASSWORD + "x"));
        assertFalse(a.verify("Admin", PASSWORD));
        assertFalse(a.verify("admin", ""));
        assertFalse(a.verify(null, PASSWORD));
    }

    @Test
    void acceptsHtpasswdStyle2yHashes() {
        // htpasswd -B emits $2y$; it is the same algorithm as $2a$/$2b$.
        String hash = new BCryptPasswordEncoder(4).encode(PASSWORD).replaceFirst("^\\$2a\\$", "\\$2y\\$");
        assertTrue(hash.startsWith("$2y$"));
        assertTrue(account(hash).verify("admin", PASSWORD));
    }

    @Test
    void blankHashDisablesLogin() {
        AdminAccount a = account("");
        assertFalse(a.isConfigured());
        assertFalse(a.verify("admin", ""));
        assertFalse(a.verify("admin", PASSWORD));
    }

    @Test
    void malformedHashFailsFast() {
        assertThrows(IllegalStateException.class, () -> account(PASSWORD));
        // What docker compose leaves behind when '$' in an unquoted .env value is interpolated away.
        assertThrows(IllegalStateException.class, () -> account("2y2abcdefghijklmnopqrstuvwxyz"));
    }
}
