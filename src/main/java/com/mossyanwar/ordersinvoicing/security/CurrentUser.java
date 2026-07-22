package com.mossyanwar.ordersinvoicing.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The name of the authenticated caller, or {@code system} for scheduled jobs and other background work. */
public final class CurrentUser {

    public static final String SYSTEM = "system";

    private CurrentUser() {
    }

    public static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
