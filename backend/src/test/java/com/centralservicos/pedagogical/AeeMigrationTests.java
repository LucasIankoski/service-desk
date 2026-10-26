package com.centralservicos.pedagogical;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AeeMigrationTests {
    @Test void upgradePreservesClassDocumentsAndBackfillsPortableNonNullPlanningScope() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:aee-migration-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("create table ped_class(id varchar(36) primary key)");
        jdbc.execute("create table ped_planning(id varchar(36) primary key,class_id varchar(36) not null,week_start date not null,theme varchar(1000),constraint uq_ped_class_week unique(class_id,week_start))");
        jdbc.execute("create table ped_project(id varchar(36) primary key,class_id varchar(36),start_date date)");
        jdbc.execute("create table ped_record(id varchar(36) primary key,class_id varchar(36),activity_date date)");
        String classId=UUID.randomUUID().toString(),planId=UUID.randomUUID().toString();
        jdbc.update("insert into ped_class(id) values(?)",classId);
        jdbc.update("insert into ped_planning(id,class_id,week_start,theme) values(?,?,'2026-10-26','Histórico preservado')",planId,classId);
        var migration=new SpringLiquibase(); migration.setDataSource(source); migration.setChangeLog("classpath:db/changelog/changes/015-aee-students.yml"); migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select aee from ped_class where id=?",Boolean.class,classId)).isFalse();
        assertThat(jdbc.queryForObject("select scope_id from ped_planning where id=?",String.class,planId)).isEqualTo(classId);
        assertThat(jdbc.queryForObject("select scope_type from ped_planning where id=?",String.class,planId)).isEqualTo("CLASS");
        assertThat(jdbc.queryForObject("select theme from ped_planning where id=?",String.class,planId)).isEqualTo("Histórico preservado");
        assertThat(jdbc.queryForObject("select count(*) from ped_planning where student_id is not null",Integer.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("insert into ped_planning(id,class_id,week_start,scope_id) values(?,?,'2026-10-26',?)",UUID.randomUUID().toString(),classId,classId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into ped_planning(id,class_id,week_start,scope_id) values(?,?,'2026-11-02',null)",UUID.randomUUID().toString(),classId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        for(int i=0;i<2;i++) {
            String studentId=UUID.randomUUID().toString();
            jdbc.update("insert into aee_student(id,aee_class_id,class_id,name,archived,row_version,created_at,updated_at) values(?,?,?,'Homônimo',false,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",studentId,classId,classId);
            jdbc.update("insert into ped_planning(id,class_id,week_start,student_id,scope_type,scope_id) values(?,?,'2026-10-26',?,'AEE_STUDENT',?)",UUID.randomUUID().toString(),classId,studentId,studentId);
        }
        assertThat(jdbc.queryForObject("select count(*) from ped_planning",Integer.class)).isEqualTo(3);
        migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("select count(*) from ped_planning",Integer.class)).isEqualTo(3);
    }
}
