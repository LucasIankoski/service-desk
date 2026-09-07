package com.centralservicos;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityMigrationTests {
    @Test
    void upgradesExistingAgendaWithoutChangingContent() throws Exception {
        verifyUpgrade(new DriverManagerDataSource("jdbc:h2:mem:priority-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    static void verifyUpgrade(DataSource dataSource) throws Exception {
        migrate(dataSource, "classpath:db/pre-priority.yml");
        var ticketFixture = TicketSimplificationMigrationTests.seed(dataSource);
        var jdbc = new JdbcTemplate(dataSource);
        var user = UUID.randomUUID().toString();
        jdbc.update("""
                insert into user_account
                (id, email, display_name, password_hash, active, password_change_required, anonymized)
                values (?, ?, ?, ?, ?, ?, ?)
                """, user, user + "@example.test", "Migration User", "placeholder", true, false, false);
        var demand = UUID.randomUUID().toString();
        var event = UUID.randomUUID().toString();
        for (var id : new String[] { demand, event }) {
            jdbc.update("""
                    insert into agenda_item
                    (id, kind_name, title, description, status_name, start_at, end_at, all_day, created_by_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, id.equals(demand) ? "INTERNAL_DEMAND" : "INSTITUTION_EVENT",
                    "Preserved title", "Preserved notes", id.equals(demand) ? "COMPLETED" : null,
                    Timestamp.from(Instant.parse("2026-09-20T12:00:00Z")),
                    Timestamp.from(Instant.parse("2026-09-20T13:00:00Z")), false, user);
        }
        jdbc.update("update agenda_item set assignee_id = ? where id = ?", user, demand);
        migrate(dataSource, "classpath:db/changelog/db.changelog-master.yml");
        TicketSimplificationMigrationTests.verify(dataSource, ticketFixture);
        migrate(dataSource, "classpath:db/changelog/db.changelog-master.yml");
        TicketSimplificationMigrationTests.verify(dataSource, ticketFixture);
        assertThat(jdbc.queryForObject("select assignee_id from agenda_item_assignee where agenda_item_id = ?", String.class, demand)).isEqualTo(user);
        assertThat(jdbc.queryForObject("select count(*) from agenda_item_assignee where agenda_item_id = ?", Integer.class, event)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from agenda_occurrence", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select priority from agenda_item where id = ?", String.class, demand)).isEqualTo("MEDIUM");
        assertThat(jdbc.queryForObject("select priority from agenda_item where id = ?", String.class, event)).isNull();
        assertThat(jdbc.queryForObject("select status_name from agenda_item where id = ?", String.class, demand)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select title from agenda_item where id = ?", String.class, demand)).isEqualTo("Preserved title");
        assertThat(jdbc.queryForObject("select row_version from agenda_item where id = ?", Long.class, demand)).isZero();
    }

    private static void migrate(DataSource dataSource, String changelog) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }
}
