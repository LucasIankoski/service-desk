package com.centralservicos.pedagogical;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Run against a disposable database only; Liquibase applies the full schema. */
@EnabledIfEnvironmentVariable(named = "PED_TEST_JDBC_URL", matches = ".+")
class ActivityRecordDatabaseIT extends ActivityRecordTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if ("isolated".equals(System.getenv("PED_TEST_SCHEMA"))) {
            registry.add("spring.liquibase.change-log", () -> "classpath:db/pedagogical-baseline.yml");
        }
        registry.add("spring.datasource.url", () -> System.getenv("PED_TEST_JDBC_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("PED_TEST_JDBC_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("PED_TEST_JDBC_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> System.getenv("PED_TEST_JDBC_DRIVER"));
    }
}
