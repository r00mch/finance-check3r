package com.financetracker.auth;

import com.financetracker.common.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the caller from the validated JWT of the current request. */
@Component
class JwtCurrentUser implements CurrentUser {

    @Override
    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return Long.valueOf(jwt.getToken().getSubject());
        }
        // unreachable for protected endpoints: the filter chain rejects unauthenticated requests first
        throw new IllegalStateException("No authenticated user in the security context");
    }
}
