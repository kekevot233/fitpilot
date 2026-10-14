package com.fitpilot.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class UserAccessGuard {

    public void check(long requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authentication is required.");
        }

        Number tokenUserId = jwt.getClaim("userId");
        if (tokenUserId == null || tokenUserId.longValue() != requestedUserId) {
            throw new AccessDeniedException("You cannot access another user's data.");
        }
    }

    public long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authentication is required.");
        }
        Number tokenUserId = jwt.getClaim("userId");
        if (tokenUserId == null) {
            throw new AccessDeniedException("The access token does not contain a user ID.");
        }
        return tokenUserId.longValue();
    }
}
