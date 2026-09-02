package com.centralservicos.identity;

import com.centralservicos.shared.DomainException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Component
public class CurrentUser {

    public AuthenticatedUser required() {
        var principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return user;
        }
        throw DomainException.forbidden("Autenticação necessária.");
    }

    public UUID id() { return required().id(); }
    public Set<Role> roles() { return required().roles(); }
    public boolean has(Role role) { return roles().contains(role); }
    public boolean operatesTickets() { return has(Role.AGENT) || has(Role.MANAGER); }
    public boolean managesTickets() { return has(Role.MANAGER); }
}
