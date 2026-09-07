package com.centralservicos;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketSimplificationMigrationTests {
    @Test
    void preservesLegacyTextAndCategoriesWithoutDuplicatingOnSecondRun() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:simplify-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
        migrate(source, "classpath:db/pre-simplify-tickets.yml");
        var fixture = seed(source);
        migrate(source, "classpath:db/changelog/db.changelog-master.yml");
        verify(source, fixture);
        migrate(source, "classpath:db/changelog/db.changelog-master.yml");
        verify(source, fixture);
    }

    static void verifyIsolatedUpgrade(DataSource source) throws Exception {
        migrate(source, "classpath:db/ticket-upgrade-baseline.yml");
        var fixture = seed(source);
        migrate(source, "classpath:db/changelog/changes/007-simplify-tickets.yml");
        verify(source, fixture);
        migrate(source, "classpath:db/changelog/changes/007-simplify-tickets.yml");
        verify(source, fixture);
    }

    static Fixture seed(DataSource source) {
        var jdbc = new JdbcTemplate(source);
        var user = UUID.randomUUID().toString();
        var category = UUID.randomUUID().toString();
        var categorized = UUID.randomUUID().toString();
        var uncategorized = UUID.randomUUID().toString();
        jdbc.update("insert into user_account (id, email, display_name, password_hash) values (?, ?, ?, ?)",
                user, user + "@example.test", "Migration", "placeholder");
        jdbc.update("insert into category (id, name) values (?, ?)", category, "Categoria " + category);
        for (var id : new String[] {categorized, uncategorized}) {
            jdbc.update("""
                    insert into ticket (id, public_number, requester_id, category_id, subject, description,
                    status_name, priority_name, due_at, deadline_warning_sent, overdue_sent)
                    values (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                    """, id, "SD-M-" + id.substring(0, 8), user, id.equals(categorized) ? category : null,
                    "A".repeat(160), "Descrição\n" + "x".repeat(7990), "OPEN", "HIGH", true, true);
        }
        return new Fixture(categorized, uncategorized, category);
    }

    static void verify(DataSource source, Fixture fixture) throws Exception {
        var jdbc = new JdbcTemplate(source);
        for (var id : new String[] {fixture.categorized(), fixture.uncategorized()}) {
            assertThat(jdbc.queryForObject("select description from ticket where id = ?", String.class, id))
                    .isEqualTo("Assunto anterior: " + "A".repeat(160) + "\n\nDescrição\n" + "x".repeat(7990));
        }
        assertThat(jdbc.queryForObject("select category_id from ticket where id = ?", String.class,
                fixture.categorized())).isEqualTo(fixture.category());
        assertThat(jdbc.queryForObject("select category_id from ticket where id = ?", String.class,
                fixture.uncategorized())).isNull();
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("select * from ticket where 1 = 0")) {
            var columns = new java.util.ArrayList<String>();
            for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                columns.add(rows.getMetaData().getColumnName(i).toLowerCase(java.util.Locale.ROOT));
            }
            assertThat(columns).doesNotContain("subject", "priority_name", "due_at", "deadline_warning_sent", "overdue_sent");
        }
    }

    private static void migrate(DataSource source, String changelog) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }

    record Fixture(String categorized, String uncategorized, String category) { }
}
