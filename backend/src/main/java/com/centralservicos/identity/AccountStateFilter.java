package com.centralservicos.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
@Component
class AccountStateFilter extends OncePerRequestFilter {

    private final IdentityService identity;

    AccountStateFilter(IdentityService identity) {
        this.identity = identity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser current)) {
            chain.doFilter(request, response);
            return;
        }

        var refreshed = identity.refreshForSession(current.id());
        if (refreshed == null || !refreshed.active()) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                    "A sessão não está mais disponível.");
            return;
        }

        var updated = UsernamePasswordAuthenticationToken.authenticated(
                refreshed, null, refreshed.getAuthorities());
        updated.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(updated);

        if (refreshed.passwordChangeRequired() && !allowedDuringPasswordChange(request)) {
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "Troque a senha temporária antes de continuar.");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean allowedDuringPasswordChange(HttpServletRequest request) {
        var path = request.getRequestURI();
        return path.equals("/api/v1/auth/me")
                || path.equals("/api/v1/auth/csrf")
                || path.equals("/api/v1/auth/password/change")
                || path.equals("/api/v1/auth/session")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/actuator/health");
    }

    private void writeProblem(HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
