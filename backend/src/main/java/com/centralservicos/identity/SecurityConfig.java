package com.centralservicos.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.time.Duration;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfCookieFilter csrfCookieFilter,
                                            AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter,
                                            AccountStateFilter accountStateFilter)
            throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName("XSRF-TOKEN");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        var requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(requestHandler))
                .securityContext(context -> context.requireExplicitSave(true))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/api/v1/public/**", "/api/v1/auth/session", "/api/v1/auth/csrf",
                                "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset",
                                "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/users/assignees").hasAnyRole("AGENT", "MANAGER")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny()))
                .addFilterAfter(csrfCookieFilter, org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                .addFilterAfter(absoluteSessionTimeoutFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .addFilterAfter(accountStateFilter, AbsoluteSessionTimeoutFilter.class);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<CsrfCookieFilter> disableCsrfCookieServletRegistration(CsrfCookieFilter filter) {
        return disabledRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<AbsoluteSessionTimeoutFilter> disableAbsoluteTimeoutServletRegistration(
            AbsoluteSessionTimeoutFilter filter) {
        return disabledRegistration(filter);
    }

    @Bean
    FilterRegistrationBean<AccountStateFilter> disableAccountStateServletRegistration(AccountStateFilter filter) {
        return disabledRegistration(filter);
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(T filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    UserDetailsService userDetailsService(IdentityService identityService) {
        return identityService::loadForAuthentication;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    Duration absoluteSessionTimeout(@Value("${app.absolute-session-timeout:12h}") Duration timeout) {
        return timeout;
    }
}
