package com.centralservicos.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    static final String AUTHENTICATED_AT = "authenticatedAt";
    private final Duration absoluteTimeout;

    AbsoluteSessionTimeoutFilter(Duration absoluteTimeout) {
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null) {
            var authenticatedAt = session.getAttribute(AUTHENTICATED_AT);
            if (authenticatedAt instanceof Long timestamp
                    && Instant.ofEpochMilli(timestamp).plus(absoluteTimeout).isBefore(Instant.now())) {
                session.invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
