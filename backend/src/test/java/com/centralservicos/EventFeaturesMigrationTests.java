package com.centralservicos;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class EventFeaturesMigrationTests {
    @Test void upgradePreservesClassAlbumsAndOccurrencesAndAllowsIndependentEvents() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:event-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table ped_record (id varchar(36) primary key, class_id varchar(36) not null, title varchar(200) not null)");
        jdbc.execute("create table agenda_occurrence (id varchar(36) primary key, body varchar(4000) not null)");
        var classId = UUID.randomUUID().toString(); var recordId = UUID.randomUUID().toString(); var noteId = UUID.randomUUID().toString();
        jdbc.update("insert into ped_record (id, class_id, title) values (?, ?, ?)", recordId, classId, "Álbum existente");
        jdbc.update("insert into agenda_occurrence (id, body) values (?, ?)", noteId, "Ocorrência existente");
        var migration = new SpringLiquibase(); migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/changes/013-general-records.yml"); migration.afterPropertiesSet();
        migration.setChangeLog("classpath:db/changelog/changes/014-daily-commitments.yml"); migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select class_id from ped_record where id = ?", String.class, recordId)).isEqualTo(classId);
        assertThat(jdbc.queryForObject("select kind_name from agenda_occurrence where id = ?", String.class, noteId)).isEqualTo("OCCURRENCE");
        jdbc.update("insert into ped_record (id, class_id, title) values (?, null, ?)", UUID.randomUUID().toString(), "Evento geral");
        jdbc.update("update agenda_occurrence set kind_name = ? where id = ?", "COMMITMENT", noteId);
        migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select kind_name from agenda_occurrence where id = ?", String.class, noteId)).isEqualTo("COMMITMENT");
        assertThat(jdbc.queryForObject("select count(*) from ped_record where class_id is null", Integer.class)).isEqualTo(1);
        jdbc.execute("create table user_account(id varchar(36) primary key)");
        migration.setChangeLog("classpath:db/changelog/changes/016-commitment-collaborator.yml"); migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select count(*) from agenda_occurrence where collaborator_id is null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select body from agenda_occurrence where id = ?", String.class,noteId)).isEqualTo("Ocorrência existente");
        var collaborator=UUID.randomUUID().toString(); jdbc.update("insert into user_account(id) values(?)",collaborator);
        jdbc.update("update agenda_occurrence set collaborator_id=? where id=?",collaborator,noteId);
        migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select collaborator_id from agenda_occurrence where id=?",String.class,noteId)).isEqualTo(collaborator);
    }
}
