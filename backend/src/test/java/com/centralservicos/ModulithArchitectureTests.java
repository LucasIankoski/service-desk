package com.centralservicos;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTests {

    @Test
    void modulesAreStructurallyValid() {
        ApplicationModules.of(ServiceDeskApplication.class).verify();
    }
}
