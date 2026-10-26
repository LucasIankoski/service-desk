package com.centralservicos.pedagogical;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ClassVisibilityMigrationTests {
    @Test void existingClassesRemainRestrictedAndRerunningPreservesSharing() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:class-visibility-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table ped_class (id varchar(36) primary key, name varchar(120) not null)");
        var id = UUID.randomUUID().toString();
        jdbc.update("insert into ped_class (id, name) values (?, ?)", id, "Legada");
        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/changes/012-pedagogical-class-visibility.yml");
        migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select content_visibility from ped_class where id = ?", String.class, id)).isEqualTo("ALLOCATED_ONLY");
        jdbc.update("update ped_class set content_visibility = ? where id = ?", "PEDAGOGICAL_READ_ONLY", id);
        migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select content_visibility from ped_class where id = ?", String.class, id)).isEqualTo("PEDAGOGICAL_READ_ONLY");
        var newId = UUID.randomUUID().toString();
        jdbc.update("insert into ped_class (id, name) values (?, ?)", newId, "Nova");
        assertThat(jdbc.queryForObject("select content_visibility from ped_class where id = ?", String.class, newId)).isEqualTo("ALLOCATED_ONLY");
    }
}
