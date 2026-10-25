package com.riotanalizer.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Static check for "is the current request made by the logged-in admin?". */
public final class AdminAccess {

    public static final String ROLE = "ADMIN";
    public static final String AUTHORITY = "ROLE_" + ROLE;

    private AdminAccess() {
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> AUTHORITY.equals(a.getAuthority()));
    }
}
