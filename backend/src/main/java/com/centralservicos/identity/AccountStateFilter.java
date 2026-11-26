package com.centralservicos.identity;

import com.centralservicos.shared.TenantContext;
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
import java.util.List;
import java.util.UUID;

/**
 * Recarrega a conta a cada requisição e define a unidade ativa.
 *
 * <p>A unidade vem do cabeçalho {@value #UNIT_HEADER} (enviado pela SPA) ou do parâmetro {@value #UNIT_PARAMETER}
 * (links de download). Ela só é aceita se o usuário tiver acesso; os perfis da requisição passam a ser os daquela
 * unidade, mais ADMIN, que é global. A unidade fica no {@link TenantContext} até o fim da requisição.</p>
 */
@Component
class AccountStateFilter extends OncePerRequestFilter {

    static final String UNIT_HEADER = "X-Unit-Id";
    static final String UNIT_PARAMETER = "unit";
    /** Rotas que funcionam sem unidade ativa: sessão, dados públicos e administração da rede. */
    private static final List<String> UNIT_FREE_PREFIXES = List.of("/api/v1/auth/", "/api/v1/public/",
            "/api/v1/admin/units", "/api/v1/admin/users", "/api/v1/admin/settings", "/api/v1/admin/audit",
            "/api/v1/notifications", "/actuator/");

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

        var requested = requestedUnit(request);
        if (requested.invalid()) {
            writeProblem(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request", "Unidade inválida.");
            return;
        }
        var session = identity.session(current.id(), requested.unitId());
        if (session == null || !session.principal().active()) {
            var httpSession = request.getSession(false);
            if (httpSession != null) {
                httpSession.invalidate();
            }
            SecurityContextHolder.clearContext();
            writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                    "A sessão não está mais disponível.");
            return;
        }
        var path = request.getRequestURI();
        if (session.requestedUnitDenied() && !path.startsWith("/api/v1/auth/")) {
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "Você não tem acesso a esta unidade.");
            return;
        }

        var refreshed = session.principal();
        var updated = UsernamePasswordAuthenticationToken.authenticated(
                refreshed, null, refreshed.getAuthorities());
        updated.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(updated);

        if (refreshed.passwordChangeRequired() && !allowedDuringPasswordChange(request)) {
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                    "Troque a senha temporária antes de continuar.");
            return;
        }
        if (session.activeUnitId() == null && requiresUnit(path)) {
            writeProblem(response, HttpServletResponse.SC_BAD_REQUEST, "Bad Request",
                    session.units().isEmpty() ? "Você não está vinculado a nenhuma unidade ativa."
                            : "Selecione a unidade.");
            return;
        }
        try (var ignored = session.activeUnitId() == null ? TenantContext.clear()
                : TenantContext.enter(session.activeUnitId())) {
            chain.doFilter(request, response);
        }
    }

    private RequestedUnit requestedUnit(HttpServletRequest request) {
        var raw = request.getHeader(UNIT_HEADER);
        if (raw == null || raw.isBlank()) {
            raw = request.getParameter(UNIT_PARAMETER);
        }
        if (raw == null || raw.isBlank()) {
            return new RequestedUnit(null, false);
        }
        try {
            return new RequestedUnit(UUID.fromString(raw.trim()), false);
        } catch (IllegalArgumentException exception) {
            return new RequestedUnit(null, true);
        }
    }

    private boolean requiresUnit(String path) {
        return path.startsWith("/api/") && UNIT_FREE_PREFIXES.stream().noneMatch(path::startsWith);
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
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }

    private record RequestedUnit(UUID unitId, boolean invalid) {
    }
}
