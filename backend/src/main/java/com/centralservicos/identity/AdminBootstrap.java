package com.centralservicos.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);
    private final IdentityService identity;
    private final String email;
    private final String password;

    AdminBootstrap(IdentityService identity,
                   @Value("${app.bootstrap.admin-email:}") String email,
                   @Value("${app.bootstrap.admin-password:}") String password) {
        this.identity = identity;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (identity.hasAdministrator()) return;
        if (email.isBlank() || password.length() < 12) {
            LOGGER.warn("Nenhum administrador existe. Defina APP_BOOTSTRAP_ADMIN_EMAIL e uma senha temporária segura.");
            return;
        }
        var created = identity.create(email, "Administrador", Set.of(Role.ADMIN), null);
        identity.setBootstrapPassword(created.user().id(), password);
        LOGGER.info("Administrador inicial criado; a troca de senha será exigida no primeiro acesso.");
    }
}
