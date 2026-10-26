package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.centralservicos.pedagogical.AeeViews.*;

@Service
class AeeService {
    private final SchoolClassRepository classes;
    private final AeeStudentRepository students;
    private final IdentityService identity;
    private final AuditService audit;
    AeeService(SchoolClassRepository classes, AeeStudentRepository students, IdentityService identity, AuditService audit) {
        this.classes=classes; this.students=students; this.identity=identity; this.audit=audit;
    }
    boolean admin(AuthenticatedUser actor) { return identity.activeUserHasAnyRole(actor.id(), Set.of(Role.ADMIN, Role.MANAGER)); }
    private void eligible(AuthenticatedUser actor) {
        if (!identity.activeUserHasAnyRole(actor.id(), Set.of(Role.ADMIN, Role.MANAGER, Role.REQUESTER))) throw DomainException.forbidden("Você não possui acesso ao Pedagógico.");
    }
    private boolean allocated(SchoolClass c, AuthenticatedUser actor) {
        return identity.activeUserHasAnyRole(actor.id(), Set.of(Role.REQUESTER)) && c.teacherIds.contains(actor.id());
    }
    private boolean editor(SchoolClass aee, SchoolClass regular, AuthenticatedUser actor) { return admin(actor) || allocated(aee, actor) || allocated(regular, actor); }
    private boolean reader(SchoolClass aee, SchoolClass regular, AuthenticatedUser actor) {
        return editor(aee, regular, actor) || aee.contentVisibility == ClassContentVisibility.PEDAGOGICAL_READ_ONLY;
    }
    private SchoolClass requiredClass(UUID id, boolean write) {
        return (write ? classes.lockById(id) : classes.findById(id)).orElseThrow(() -> DomainException.notFound("Turma não encontrada."));
    }
    private SchoolClass aee(UUID id, boolean write) {
        var c=requiredClass(id,write); if (!c.aee) throw DomainException.notFound("AEE não encontrado."); return c;
    }
    private SchoolClass regular(UUID id, boolean write) {
        var c=requiredClass(id,write); if (c.aee) throw DomainException.notFound("Turma regular não encontrada."); return c;
    }
    boolean hasRegularAllocation(AuthenticatedUser actor) {
        return identity.activeUserHasAnyRole(actor.id(), Set.of(Role.REQUESTER)) && classes.hasRegularAllocation(actor.id());
    }
    void checkTypeChange(SchoolClass c, boolean requested) {
        if (c.aee != requested && (c.aee ? students.existsByAeeClassId(c.id) : students.existsByClassId(c.id)))
            throw DomainException.unprocessable("O tipo da turma não pode mudar enquanto houver vínculos de alunos AEE. Arquive a turma para preservar o histórico.");
    }
    @Transactional(readOnly=true)
    public List<Classroom> directory(UUID aeeId, boolean archived, AuthenticatedUser actor) {
        eligible(actor); var parent=aee(aeeId,false);
        if (!(admin(actor) || allocated(parent,actor) || parent.contentVisibility==ClassContentVisibility.PEDAGOGICAL_READ_ONLY || hasRegularAllocation(actor)))
            throw DomainException.forbidden("Você não possui acesso a este AEE.");
        return classes.findAllByOrderByNameAsc().stream().filter(c -> !c.aee && (archived || !c.archived))
                .map(c -> new Classroom(c.id,c.name,c.archived,reader(parent,c,actor),!parent.archived&&!c.archived&&editor(parent,c,actor))).toList();
    }
    @Transactional(readOnly=true)
    public List<Student> students(UUID aeeId, UUID classId, AuthenticatedUser actor) {
        eligible(actor); var p=aee(aeeId,false); var c=regular(classId,false); authorize(p,c,actor,false);
        return students.findByAeeClassIdAndClassIdOrderByNameAscIdAsc(aeeId,classId).stream().map(s -> view(s,!p.archived&&!c.archived&&editor(p,c,actor))).toList();
    }
    @Transactional
    public Student save(UUID aeeId, UUID classId, UUID id, StudentInput input, AuthenticatedUser actor) {
        eligible(actor); var p=aee(aeeId,true); var c=regular(classId,true); authorize(p,c,actor,true);
        var s=id==null ? new AeeStudent() : required(id,true);
        if (id!=null) {
            matches(s,aeeId,classId);
            if (!Objects.equals(s.rowVersion,input.version())) throw DomainException.conflict("Este aluno mudou. Consulte a versão atual antes de salvar novamente.");
            if (s.archived && input.archived()) throw DomainException.unprocessable("Reative o aluno antes de alterar seu cadastro.");
        }
        s.aeeClassId=aeeId; s.classId=classId; s.name=input.name().strip(); s.archived=input.archived(); s.updatedAt=java.time.Instant.now();
        students.saveAndFlush(s); audit.record(actor.id(),id==null?"AEE_STUDENT_CREATED":"AEE_STUDENT_UPDATED","AeeStudent",s.id,null);
        return view(s,true);
    }
    private AeeStudent required(UUID id, boolean write) { return (write?students.lockById(id):students.findById(id)).orElseThrow(() -> DomainException.notFound("Aluno não encontrado.")); }
    private void matches(AeeStudent s, UUID aeeId, UUID classId) { if (!s.aeeClassId.equals(aeeId)||!s.classId.equals(classId)) throw DomainException.notFound("Aluno não encontrado neste contexto."); }
    private void authorize(SchoolClass p, SchoolClass c, AuthenticatedUser actor, boolean write) {
        if (!(write?editor(p,c,actor):reader(p,c,actor))) throw DomainException.forbidden("Você não possui permissão para esta turma no AEE.");
        if (write && (p.archived||c.archived)) throw DomainException.unprocessable("Reative o AEE e a turma antes de alterar o conteúdo.");
    }
    @Transactional(readOnly=true)
    public Context context(UUID aeeId, UUID classId, UUID studentId, AuthenticatedUser actor) {
        eligible(actor); var s=required(studentId,false); matches(s,aeeId,classId);
        var p=aee(aeeId,false); var c=regular(classId,false); authorize(p,c,actor,false);
        return new Context(p.id,p.name,c.id,c.name,view(s,!p.archived&&!c.archived&&editor(p,c,actor)),p.archived||c.archived||s.archived,
                !p.archived&&!c.archived&&!s.archived&&editor(p,c,actor),admin(actor),teachers(p,c));
    }
    @Transactional
    public SchoolClass contentClass(UUID studentId, AuthenticatedUser actor, boolean write) {
        eligible(actor);
        // Read the IDs first, then acquire locks consistently: AEE, regular class, student.
        var ref=required(studentId,false); var p=aee(ref.aeeClassId,write); var c=regular(ref.classId,write); var s=required(studentId,write);
        authorize(p,c,actor,write);
        if (write && s.archived) throw DomainException.unprocessable("Reative o aluno antes de alterar seus documentos.");
        var result=new SchoolClass("AEE: "+p.name+" · Turma: "+c.name+" · Aluno: "+s.name);
        result.id=c.id; result.archived=p.archived||c.archived||s.archived; result.teacherIds.addAll(p.teacherIds); result.teacherIds.addAll(c.teacherIds);
        result.rowVersion=c.rowVersion; return result;
    }
    private List<PedagogicalViews.Teacher> teachers(SchoolClass p, SchoolClass c) {
        var ids=new HashSet<>(p.teacherIds); ids.addAll(c.teacherIds);
        return identity.displayNames(ids).entrySet().stream().filter(e -> identity.activeUserHasAnyRole(e.getKey(),Set.of(Role.REQUESTER)))
                .sorted(Map.Entry.comparingByValue()).map(e -> new PedagogicalViews.Teacher(e.getKey(),e.getValue())).toList();
    }
    String route(UUID studentId) {
        var s=required(studentId,false);
        return "/api/v1/pedagogical/aee/"+s.aeeClassId+"/classes/"+s.classId+"/students/"+s.id;
    }
    private Student view(AeeStudent s,boolean manage) { return new Student(s.id,s.aeeClassId,s.classId,s.name,s.archived,s.rowVersion,manage); }
}
