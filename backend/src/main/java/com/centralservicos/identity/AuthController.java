package com.centralservicos.identity;

import com.centralservicos.shared.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final LoginAttemptService attempts;
    private final IdentityService identity;
    private final CurrentUser currentUser;

    AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                   LoginAttemptService attempts, IdentityService identity, CurrentUser currentUser) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.attempts = attempts;
        this.identity = identity;
        this.currentUser = currentUser;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/session")
    MeResponse login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
                     HttpServletResponse response) {
        if (attempts.blocked(login.email())) {
            throw new DomainException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde 15 minutos antes de tentar novamente.");
        }
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
            request.getSession(true).setAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT,
                    System.currentTimeMillis());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            attempts.succeeded(login.email());
            identity.recordAuthentication(((AuthenticatedUser) authentication.getPrincipal()).id(), "LOGIN_SUCCEEDED");
            return MeResponse.from((AuthenticatedUser) authentication.getPrincipal());
        } catch (AuthenticationException exception) {
            attempts.failed(login.email());
            identity.recordAuthentication(null, "LOGIN_FAILED");
            throw new DomainException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        identity.recordAuthentication(currentUser.id(), "LOGOUT");
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    MeResponse me() {
        return MeResponse.from(currentUser.required());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody PasswordRequest body) {
        identity.changePassword(currentUser.id(), body.password());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgot(@Valid @RequestBody ForgotPasswordRequest body) {
        identity.requestReset(body.email());
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody ResetPasswordRequest body) {
        identity.resetPassword(body.token(), body.password());
    }

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    record PasswordRequest(@NotBlank @Size(min = 12, max = 128) String password) { }
    record ForgotPasswordRequest(@NotBlank @Email String email) { }
    record ResetPasswordRequest(@NotBlank String token,
                                @NotBlank @Size(min = 12, max = 128) String password) { }

    record MeResponse(java.util.UUID id, String email, String displayName, java.util.Set<Role> roles,
                      boolean passwordChangeRequired) {
        static MeResponse from(AuthenticatedUser user) {
            return new MeResponse(user.id(), user.email(), user.displayName(), user.roles(),
                    user.passwordChangeRequired());
        }
    }
}
