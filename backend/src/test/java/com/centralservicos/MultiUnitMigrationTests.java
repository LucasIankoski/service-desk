package com.centralservicos;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A 017 move a instalação atual para a unidade inicial sem perder dados nem permissões. */
class MultiUnitMigrationTests {

    private static final String UNIT = TestUnits.DEFAULT_UNIT_ID.toString();

    @Test
    void movesExistingDataAndRolesIntoTheInitialUnit() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:multi-unit-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
        migrate(source, "classpath:db/pre-multi-unit.yml");
        var jdbc = new JdbcTemplate(source);
        var requester = user(jdbc, "REQUESTER");
        var admin = user(jdbc, "ADMIN", "AGENT");
        var legacy = user(jdbc, "GUARDIAN");
        var category = UUID.randomUUID().toString();
        var ticket = UUID.randomUUID().toString();
        var schoolClass = UUID.randomUUID().toString();
        jdbc.update("update app_settings set institution_name = ?, support_email = ? where id = 1",
                "Escola Amor e Graça", "suporte@example.test");
        jdbc.update("insert into category (id, name) values (?, ?)", category, "Manutenção");
        jdbc.update("insert into ticket (id, public_number, requester_id, category_id, description, status_name) "
                + "values (?, ?, ?, ?, ?, ?)", ticket, "SD-2026-000007", requester, category, "Pedido", "OPEN");
        jdbc.update("insert into ticket_counter (counter_year, next_value) values (?, ?)", 2026, 8);
        jdbc.update("insert into ped_class (id, name, archived, row_version, created_at, updated_at) "
                + "values (?, ?, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", schoolClass, "Turma A");

        migrate(source, "classpath:db/changelog/db.changelog-master.yml");
        verify(jdbc, requester, admin, legacy, ticket, schoolClass, category);
        migrate(source, "classpath:db/changelog/db.changelog-master.yml");
        verify(jdbc, requester, admin, legacy, ticket, schoolClass, category);

        var otherUnit = UUID.randomUUID().toString();
        jdbc.update("insert into school_unit (id, code, name) values (?, ?, ?)", otherUnit, "U2", "Unidade 2");
        jdbc.update("insert into category (id, name, unit_id) values (?, ?, ?)", UUID.randomUUID().toString(),
                "Manutenção", otherUnit);
        assertThat(jdbc.queryForObject("select count(*) from category where name = 'Manutenção'", Integer.class))
                .isEqualTo(2);
    }

    private static void verify(JdbcTemplate jdbc, String requester, String admin, String legacy, String ticket,
                               String schoolClass, String category) {
        assertThat(jdbc.queryForMap("select code, name, support_email from school_unit where id = ?", UNIT))
                .containsEntry("code", "SD")
                .containsEntry("name", "Escola Amor e Graça")
                .containsEntry("support_email", "suporte@example.test");
        assertThat(jdbc.queryForList("select role_name from unit_membership where user_id = ? and unit_id = ?",
                String.class, requester, UNIT)).containsExactly("REQUESTER");
        assertThat(jdbc.queryForList("select role_name from unit_membership where user_id = ?", String.class, admin))
                .containsExactly("AGENT");
        assertThat(jdbc.queryForList("select role_name from user_role where user_id = ?", String.class, admin))
                .containsExactly("ADMIN");
        assertThat(jdbc.queryForList("select role_name from user_role where user_id = ?", String.class, legacy))
                .containsExactly("GUARDIAN");
        assertThat(jdbc.queryForList("select role_name from user_role where user_id = ?", String.class, requester))
                .isEmpty();
        for (var table : new String[] {"ticket", "ped_class", "category"}) {
            var id = switch (table) {
                case "ticket" -> ticket;
                case "ped_class" -> schoolClass;
                default -> category;
            };
            assertThat(jdbc.queryForObject("select unit_id from " + table + " where id = ?", String.class, id))
                    .isEqualTo(UNIT);
        }
        assertThat(jdbc.queryForMap("select unit_id, next_value from unit_ticket_counter where counter_year = 2026"))
                .containsEntry("unit_id", UNIT)
                .containsEntry("next_value", 8L);
    }

    private static String user(JdbcTemplate jdbc, String... roles) {
        var id = UUID.randomUUID().toString();
        jdbc.update("insert into user_account (id, email, display_name, password_hash) values (?, ?, ?, ?)",
                id, id + "@example.test", "Migração", "placeholder");
        for (var role : roles) {
            jdbc.update("insert into user_role (user_id, role_name) values (?, ?)", id, role);
        }
        return id;
    }

    private static void migrate(DataSource source, String changelog) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }
}
