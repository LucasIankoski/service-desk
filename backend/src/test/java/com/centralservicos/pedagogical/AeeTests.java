package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import static com.centralservicos.pedagogical.PedagogicalViews.*;
import static com.centralservicos.pedagogical.ClassContentVisibility.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:aee_tests;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional
class AeeTests {
    @Autowired PedagogicalService classes;
    @Autowired AeeService aee;
    @Autowired ProjectService projects;
    @Autowired ActivityRecordService records;
    @Autowired AeeDocumentService documents;
    @Autowired IdentityService identity;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;

    record Setup(AuthenticatedUser admin,AuthenticatedUser specialist,AuthenticatedUser teacher,AuthenticatedUser reader,ClassView parent,ClassView regular) {}
    Setup setup(boolean shared) {
        var admin=actor(Role.ADMIN); var specialist=actor(Role.REQUESTER); var teacher=actor(Role.REQUESTER); var reader=actor(Role.REQUESTER);
        var p=classes.saveClass(null,new ClassInput("Atendimento Especial",false,List.of(specialist.id()),null,shared?PEDAGOGICAL_READ_ONLY:ALLOCATED_ONLY,true),admin);
        var c=classes.saveClass(null,new ClassInput("Turma regular",false,List.of(teacher.id()),null,ALLOCATED_ONLY,false),admin);
        return new Setup(admin,specialist,teacher,reader,p,c);
    }
    AeeViews.Student student(Setup x,String name) { return aee.save(x.parent.id(),x.regular.id(),null,new AeeViews.StudentInput(name,false,null),x.specialist); }
    String base(Setup x,AeeViews.Student s) { return "/api/v1/pedagogical/aee/"+x.parent.id()+"/classes/"+x.regular.id()+"/students/"+s.id(); }
    @Test void discoveryAndRestrictedCardsDoNotExposeStudentsAndBothAllocationsCanManage() {
        var x=setup(false); var s=student(x,"Maria da Silva"); var other=classes.saveClass(null,new ClassInput("Outra turma",false,List.of(),null),x.admin);
        assertThat(classes.classes(x.teacher)).extracting(ClassView::id).contains(x.parent.id());
        assertThat(classes.getClass(x.parent.id(),x.teacher).aee()).isTrue();
        var cards=aee.directory(x.parent.id(),false,x.teacher);
        assertThat(cards).noneMatch(c -> c.id().equals(x.parent.id()));
        assertThat(cards.stream().filter(c -> c.id().equals(other.id())).findFirst().orElseThrow().canView()).isFalse();
        assertThat(aee.students(x.parent.id(),x.regular.id(),x.teacher)).extracting(AeeViews.Student::id).containsExactly(s.id());
        var renamed=aee.save(x.parent.id(),x.regular.id(),s.id(),new AeeViews.StudentInput("Maria Renomeada",false,s.version()),x.teacher);
        assertThat(renamed.name()).isEqualTo("Maria Renomeada");
        assertThatThrownBy(() -> aee.students(x.parent.id(),other.id(),x.teacher)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> aee.students(x.parent.id(),x.regular.id(),x.reader)).isInstanceOf(DomainException.class);
    }
    @Test void homonymsAndDifferentAeesHaveIndependentStudentsAndPlanningWeeks() {
        var x=setup(true); var s=student(x,"Maria"); var same=student(x,"Maria");
        var p2=classes.saveClass(null,new ClassInput("Segundo AEE",false,List.of(x.specialist.id()),null,ALLOCATED_ONLY,true),x.admin);
        var s2=aee.save(p2.id(),x.regular.id(),null,new AeeViews.StudentInput("Maria",false,null),x.specialist);
        assertThat(Set.of(s.id(),same.id(),s2.id())).hasSize(3);
        var week=LocalDate.of(2026,10,26);
        var ordinary=classes.create(x.regular.id(),week,x.teacher);
        var one=classes.create(x.regular.id(),week,x.specialist,s.id());
        var two=classes.create(x.regular.id(),week,x.teacher,same.id());
        classes.create(x.regular.id(),week,x.specialist,s2.id());
        assertThatThrownBy(() -> classes.create(x.regular.id(),week,x.specialist,s.id())).hasMessageContaining("semana");
        assertThat(classes.list(x.regular.id(),null,null,null,null,x.teacher)).extracting(Summary::id).containsExactly(ordinary.id());
        assertThat(classes.list(x.regular.id(),null,null,null,null,x.specialist,s.id())).extracting(Summary::id).containsExactly(one.id());
        assertThat(classes.list(x.regular.id(),null,null,null,null,x.teacher,same.id())).extracting(Summary::id).containsExactly(two.id());
        assertThat(classes.get(one.id(),x.reader,s.id()).className()).contains("Maria","Atendimento Especial","Turma regular");
        assertThatThrownBy(() -> aee.context(p2.id(),x.regular.id(),s.id(),x.admin)).hasMessageContaining("contexto");
    }
    @Test void legacyConversionPreservesReadOnlyDocumentsAndTypeChangesCannotBreakStudentLinks() {
        var x=setup(false); var old=classes.create(x.regular.id(),LocalDate.of(2026,10,26),x.teacher);
        var converted=classes.saveClass(x.regular.id(),new ClassInput(x.regular.name(),false,List.of(x.teacher.id()),x.regular.version(),ALLOCATED_ONLY,true),x.admin);
        assertThat(classes.get(old.id(),x.teacher).id()).isEqualTo(old.id());
        assertThat(classes.getClass(converted.id(),x.teacher).canEdit()).isFalse();
        assertThatThrownBy(() -> classes.create(converted.id(),LocalDate.of(2026,11,2),x.admin)).hasMessageContaining("consulta");
        assertThatThrownBy(() -> classes.delete(old.id(),old.version(),x.admin)).hasMessageContaining("consulta");
        var regular=classes.saveClass(null,new ClassInput("Nova regular",false,List.of(x.teacher.id()),null),x.admin);
        aee.save(converted.id(),regular.id(),null,new AeeViews.StudentInput("Aluno",false,null),x.teacher);
        assertThatThrownBy(() -> classes.saveClass(converted.id(),new ClassInput(converted.name(),false,List.of(),converted.version(),ALLOCATED_ONLY,false),x.admin)).hasMessageContaining("tipo");
        assertThatThrownBy(() -> classes.saveClass(regular.id(),new ClassInput(regular.name(),false,List.of(),regular.version(),ALLOCATED_ONLY,true),x.admin)).hasMessageContaining("tipo");
    }
    @Test void studentArchiveKeepsReadHistoryBlocksAllWritesAndReactivatesWithVersion() {
        var x=setup(true); var s=student(x,"Aluno"); var p=classes.create(x.regular.id(),LocalDate.of(2026,10,26),x.teacher,s.id());
        var archived=aee.save(x.parent.id(),x.regular.id(),s.id(),new AeeViews.StudentInput(s.name(),true,s.version()),x.teacher);
        assertThat(aee.context(x.parent.id(),x.regular.id(),s.id(),x.reader).canEdit()).isFalse();
        assertThat(classes.get(p.id(),x.reader,s.id()).archived()).isTrue();
        assertThatThrownBy(() -> classes.delete(p.id(),p.version(),x.admin,s.id())).hasMessageContaining("Reative");
        assertThatThrownBy(() -> documents.upload(s.id(),pdf(),x.teacher)).hasMessageContaining("Reative");
        assertThatThrownBy(() -> aee.save(x.parent.id(),x.regular.id(),s.id(),new AeeViews.StudentInput(s.name(),false,s.version()),x.teacher)).hasMessageContaining("mudou");
        aee.save(x.parent.id(),x.regular.id(),s.id(),new AeeViews.StudentInput(s.name(),false,archived.version()),x.teacher);
        assertThat(aee.context(x.parent.id(),x.regular.id(),s.id(),x.teacher).canEdit()).isTrue();
    }
    @Test void parentAndRegularArchiveBlockWritesButKeepAuthorizedConsultation() {
        for (boolean parent : List.of(true,false)) {
            var x=setup(true); var s=student(x,"Aluno"); var c=parent?x.parent:x.regular;
            classes.saveClass(c.id(),new ClassInput(c.name(),true,c.teachers().stream().map(Teacher::id).toList(),c.version()),x.admin);
            assertThat(aee.context(x.parent.id(),x.regular.id(),s.id(),x.reader).archived()).isTrue();
            assertThatThrownBy(() -> aee.save(x.parent.id(),x.regular.id(),null,new AeeViews.StudentInput("Novo",false,null),x.specialist)).hasMessageContaining("Reative");
            assertThatThrownBy(() -> projects.create(x.regular.id(),new ProjectViews.CreateInput("Novo",LocalDate.of(2026,10,1),LocalDate.of(2026,10,2)),x.teacher,s.id())).hasMessageContaining("Reative");
        }
    }
    @Test void revocationAndInactiveAccountsRecheckAccessIncludingDirectFiles() {
        var x=setup(false); var s=student(x,"Aluno"); var d=documents.upload(s.id(),pdf(),x.teacher);
        classes.saveClass(x.regular.id(),new ClassInput(x.regular.name(),false,List.of(),x.regular.version()),x.admin);
        assertThatThrownBy(() -> documents.load(s.id(),d.id(),x.teacher)).isInstanceOf(DomainException.class);
        assertThat(aee.context(x.parent.id(),x.regular.id(),s.id(),x.specialist).canEdit()).isTrue();
        identity.update(x.specialist.id(),x.specialist.displayName(),Set.of(Role.REQUESTER),false,x.admin.id());
        assertThatThrownBy(() -> documents.load(s.id(),d.id(),x.specialist)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> aee.directory(x.parent.id(),false,actor(Role.AGENT))).isInstanceOf(DomainException.class);
    }
    @Test void recordsEnforceFiftyAndAuthorshipWhileProjectsAreSharedAndAdministrativeDeletionRemains() throws Exception {
        var x=setup(true); var s=student(x,"Aluno"); var input=new ActivityRecordViews.Input("Fotos",LocalDate.of(2026,10,25),null,List.of());
        assertThatThrownBy(() -> records.create(x.regular.id(),input,Collections.nCopies(51,ActivityRecordTests.png()),x.teacher,s.id())).hasMessageContaining("1 e 50");
        var r=records.create(x.regular.id(),input,Collections.nCopies(50,ActivityRecordTests.png()),x.teacher,s.id());
        assertThat(r.canEdit()).isTrue(); assertThat(records.get(r.id(),x.specialist,s.id()).canEdit()).isFalse();
        assertThatThrownBy(() -> records.delete(r.id(),r.version(),x.specialist,s.id())).hasMessageContaining("autora");
        assertThat(records.list(x.regular.id(),null,null,0,x.teacher).items()).isEmpty();
        assertThat(records.listGeneral(null,null,0,x.teacher).items()).noneMatch(t -> t.id().equals(r.id()));
        var p=projects.create(x.regular.id(),new ProjectViews.CreateInput("Projeto",LocalDate.of(2026,10,1),LocalDate.of(2026,10,2)),x.teacher,s.id());
        var rich=ProjectViews.RichText.empty();
        var edit=new ProjectViews.EditInput(p.version(),"Editado pela AEE",p.startDate(),p.endDate(),"",List.of(x.specialist.id()),rich,List.of(),List.of(),rich);
        var changed=projects.edit(p.id(),edit,x.specialist,s.id());
        assertThat(changed.title()).isEqualTo("Editado pela AEE");
        assertThatThrownBy(() -> projects.edit(p.id(),edit,x.teacher,s.id())).hasMessageContaining("mudou");
        assertThatThrownBy(() -> projects.delete(p.id(),changed.version(),x.teacher,s.id())).hasMessageContaining("administrativo");
        projects.delete(p.id(),changed.version(),x.admin,s.id()); records.delete(r.id(),r.version(),x.admin,s.id());
    }
    @Test void httpScopeRejectsCrossStudentCrossAeeAndLegacyUrlsIncludingMutationsAndPhotos() throws Exception {
        var x=setup(true); var s=student(x,"Aluno 1"); var s2=student(x,"Aluno 2");
        var plan=classes.create(x.regular.id(),LocalDate.of(2026,10,26),x.teacher,s.id());
        var project=projects.create(x.regular.id(),new ProjectViews.CreateInput("Projeto",LocalDate.of(2026,10,1),LocalDate.of(2026,10,2)),x.teacher,s.id());
        var r=records.create(x.regular.id(),new ActivityRecordViews.Input("Fotos",LocalDate.of(2026,10,25),null,List.of()),List.of(ActivityRecordTests.png()),x.teacher,s.id());
        var d=documents.upload(s.id(),pdf(),x.teacher); var mvc=mvc();
        for(var resource:List.of("plans/"+plan.id(),"projects/"+project.id(),"records/"+r.id(),"records/"+r.id()+"/photos/"+r.photos().getFirst().id())) {
            mvc.perform(get(base(x,s2)+"/"+resource).with(user(x.admin))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/pedagogical/"+resource).with(user(x.admin))).andExpect(status().isNotFound());
        }
        mvc.perform(delete(base(x,s2)+"/plans/"+plan.id()).param("version","0").with(user(x.admin)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(base(x,s2)+"/plans/"+plan.id()).param("version","0").with(user(x.teacher)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/pedagogical/projects/"+project.id()).param("version","0").with(user(x.teacher)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(base(x,s2)+"/documents/"+d.id()).with(user(x.admin))).andExpect(status().isNotFound());
        mvc.perform(get(base(x,s)+"/records/"+r.id()+"/photos/"+r.photos().getFirst().id()).with(user(x.reader))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(base(x,s).replace(x.parent.id().toString(),x.regular.id().toString())+"/plans").with(user(x.admin))).andExpect(status().isNotFound());
    }
    @Test void pdfUploadAndAuthenticatedReadEnforceTypeSizeOwnerVersionAndDurableCleanup() throws Exception {
        var x=setup(true); var s=student(x,"Aluno"); var mvc=mvc();
        mvc.perform(multipart(base(x,s)+"/documents").file(pdf()).with(user(x.teacher)).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.filename").value("documento.pdf"));
        var d=documents.list(s.id(),x.teacher).items().getFirst();
        mvc.perform(get(base(x,s)+"/documents/"+d.id()).with(user(x.reader))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get(base(x,s)+"/documents/"+d.id()).param("download","true").with(user(x.reader))).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment")));
        mvc.perform(get(base(x,s)+"/documents/"+d.id())).andExpect(status().isUnauthorized());
        assertThat(documents.list(s.id(),x.reader).canUpload()).isFalse();
        assertThatThrownBy(() -> documents.upload(s.id(),new MockMultipartFile("file","fake.pdf","application/pdf","not pdf".getBytes()),x.teacher)).hasMessageContaining("assinatura");
        assertThatThrownBy(() -> documents.upload(s.id(),new MockMultipartFile("file","wrong.txt","application/pdf",pdf().getBytes()),x.teacher)).hasMessageContaining("extensão");
        assertThatThrownBy(() -> documents.upload(s.id(),new MockMultipartFile("file","large.pdf","application/pdf",new byte[26*1024*1024]),x.teacher)).hasMessageContaining("limite");
        assertThatThrownBy(() -> documents.delete(s.id(),d.id(),d.version(),x.specialist)).hasMessageContaining("autora");
        assertThatThrownBy(() -> documents.delete(s.id(),d.id(),d.version()+1,x.teacher)).hasMessageContaining("mudou");
        documents.delete(s.id(),d.id(),d.version(),x.admin);
        assertThatThrownBy(() -> documents.load(s.id(),d.id(),x.teacher)).hasMessageContaining("encontrado");
    }
    static MockMultipartFile pdf() { return new MockMultipartFile("file","documento.pdf","application/pdf","%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
    MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    AuthenticatedUser actor(Role role) {
        var u=identity.create(UUID.randomUUID()+"@example.test","Pessoa "+UUID.randomUUID(),Set.of(role),null).user();
        identity.changePassword(u.id(),"permanent-password-123");
        return new AuthenticatedUser(u.id(),u.email(),u.displayName(),"unused",u.roles(),true,false);
    }
}
