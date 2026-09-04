package com.centralservicos;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCompatibilityIT {

    @Test
    void migrationsAndCoreFlowWorkOnPostgreSQL() throws Exception {
        verifyDatabase("postgresql", () -> new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine")));
    }

    @Test
    void migrationsAndCoreFlowWorkOnMySQL() throws Exception {
        verifyDatabase("mysql", () -> new MySQLContainer<>(DockerImageName.parse("mysql:8.4")));
    }

    @Test
    void migrationsAndCoreFlowWorkOnOracle() throws Exception {
        verifyDatabase("oracle", () -> new OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart")));
    }

    @Test
    void migrationsAndCoreFlowWorkOnSqlServer() throws Exception {
        verifyDatabase("sqlserver", () -> new MSSQLServerContainer<>(
                DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest")).acceptLicense());
    }

    private void verifyDatabase(String vendor, Supplier<JdbcDatabaseContainer<?>> databaseSupplier) throws Exception {
        if (!selected(vendor)) {
            return;
        }
        assumeDockerAvailable();
        try (var database = databaseSupplier.get()) {
            database.start();
            migrateAndExercise(database);
        }
    }

    private boolean selected(String vendor) {
        var requested = System.getProperty("db.compatibility.vendor", "all");
        return "all".equalsIgnoreCase(requested) || vendor.equalsIgnoreCase(requested);
    }

    private void assumeDockerAvailable() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new TestAbortedException("Docker is not available for database compatibility tests.");
            }
        } catch (RuntimeException exception) {
            throw new TestAbortedException("Docker is not available for database compatibility tests.", exception);
        }
    }

    private void migrateAndExercise(JdbcDatabaseContainer<?> database) throws Exception {
        var dataSource = new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
        dataSource.setDriverClassName(database.getDriverClassName());

        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yml");
        liquibase.afterPropertiesSet();

        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select count(*) from app_settings", Integer.class)).isEqualTo(1);

        var requesterId = UUID.randomUUID().toString();
        var ticketId = UUID.randomUUID().toString();
        var commentId = UUID.randomUUID().toString();
        var agendaItemId = UUID.randomUUID().toString();
        var publicNumber = "SD-2099-" + requesterId.substring(0, 6).toUpperCase();

        jdbc.update("""
                insert into user_account
                (id, email, display_name, password_hash, active, password_change_required, anonymized)
                values (?, ?, ?, ?, ?, ?, ?)
                """, requesterId, "compat-" + requesterId + "@example.test", "Compatibility User",
                "argon2id-placeholder", true, false, false);
        jdbc.update("""
                insert into ticket
                (id, public_number, requester_id, subject, description, status_name, priority_name)
                values (?, ?, ?, ?, ?, ?, ?)
                """, ticketId, publicNumber, requesterId, "Compatibility ticket",
                "Portable schema smoke test", "OPEN", "NORMAL");
        jdbc.update("""
                insert into ticket_comment
                (id, ticket_id, author_id, body, visibility_name)
                values (?, ?, ?, ?, ?)
                """, commentId, ticketId, requesterId, "Portable comment", "PUBLIC");

        var joinedRows = jdbc.queryForObject("""
                select count(*)
                from ticket_comment c
                join ticket t on t.id = c.ticket_id
                join user_account u on u.id = c.author_id
                where t.public_number = ?
                """, Integer.class, publicNumber);
        assertThat(joinedRows).isEqualTo(1);

        jdbc.update("insert into user_role (user_id, role_name) values (?, ?)", requesterId, "MANAGER");
        jdbc.update("""
                insert into agenda_item
                (id, kind_name, title, location, start_at, end_at, all_day, created_by_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, agendaItemId, "INSTITUTION_EVENT", "Compatibility event", "Main room",
                Timestamp.from(Instant.parse("2026-09-20T12:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-20T13:00:00Z")), false, requesterId);
        var agendaRows = jdbc.queryForObject("""
                select count(*)
                from agenda_item a
                join user_account u on u.id = a.created_by_id
                where a.id = ? and a.kind_name = ?
                """, Integer.class, agendaItemId, "INSTITUTION_EVENT");
        assertThat(agendaRows).isEqualTo(1);
    }
}
