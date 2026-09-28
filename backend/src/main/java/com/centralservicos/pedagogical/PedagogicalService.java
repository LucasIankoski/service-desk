package com.centralservicos.pedagogical;
import com.centralservicos.identity.*;
import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.centralservicos.pedagogical.PedagogicalViews.*;

@Service
public class PedagogicalService {
    private final SchoolClassRepository classes;
    private final WeeklyPlanningRepository plans;
    private final IdentityService identity;
    private final AuditService audit;
    PedagogicalService(SchoolClassRepository classes, WeeklyPlanningRepository plans, IdentityService identity, AuditService audit) {
        this.classes = classes; this.plans = plans; this.identity = identity; this.audit = audit;
    }
    boolean administrative(AuthenticatedUser actor) {
        return identity.activeUserHasAnyRole(actor.id(), Set.of(Role.ADMIN, Role.MANAGER));
    }
    private void eligible(AuthenticatedUser actor) {
        if (!identity.activeUserHasAnyRole(actor.id(), Set.of(Role.ADMIN, Role.MANAGER, Role.REQUESTER)))
            throw DomainException.forbidden("Você não possui acesso ao Pedagógico.");
    }
    private void admin(AuthenticatedUser actor) {
        eligible(actor);
        if (!administrative(actor)) throw DomainException.forbidden("Esta ação exige perfil administrativo.");
    }
    SchoolClass classroom(UUID id, AuthenticatedUser actor, boolean write) {
        eligible(actor);
        var row = (write ? classes.lockById(id) : classes.findById(id))
                .orElseThrow(() -> DomainException.notFound("Turma não encontrada."));
        if (!administrative(actor) && (!identity.activeUserHasAnyRole(actor.id(), Set.of(Role.REQUESTER)) || !row.teacherIds.contains(actor.id())))
            throw DomainException.forbidden("Você não está alocada nesta turma.");
        return row;
    }
    private void writable(SchoolClass row) {
        if (row.archived) throw DomainException.unprocessable("Reative a turma antes de alterar seus planejamentos.");
    }
    private void version(Long actual, Long expected) {
        if (expected == null) throw DomainException.unprocessable("Informe a versão do registro.");
        if (!Objects.equals(actual, expected)) throw DomainException.conflict("Este registro mudou. Seu texto foi preservado; consulte a versão atual antes de salvar novamente.");
    }
    @Transactional(readOnly=true)
    public List<ClassView> classes(AuthenticatedUser actor) {
        eligible(actor);
        return (administrative(actor) ? classes.findAllByOrderByNameAsc() : classes.findAllocated(actor.id()))
                .stream().map(this::classView).toList();
    }
    @Transactional(readOnly=true)
    public ClassView getClass(UUID id, AuthenticatedUser actor) { return classView(classroom(id, actor, false)); }
    @Transactional(readOnly=true)
    public List<Teacher> teachers(AuthenticatedUser actor) {
        admin(actor);
        return identity.activeRequesters().stream().map(t -> new Teacher(t.id(), t.displayName())).toList();
    }
    @Transactional
    public ClassView saveClass(UUID id, ClassInput input, AuthenticatedUser actor) {
        admin(actor);
        var row = id == null ? new SchoolClass(input.name().trim()) : classroom(id, actor, true);
        if (id != null) version(row.rowVersion, input.version());
        if (row.archived && input.archived()) throw DomainException.unprocessable("Reative a turma antes de alterar seu cadastro.");
        for (var teacher : input.teacherIds()) {
            if (!row.teacherIds.contains(teacher) && !identity.activeUserHasAnyRole(teacher, Set.of(Role.REQUESTER)))
                throw DomainException.unprocessable("Aloque apenas professoras solicitantes ativas.");
        }
        row.name = input.name().trim(); row.archived = input.archived();
        row.teacherIds.clear(); row.teacherIds.addAll(input.teacherIds()); row.touch();
        classes.saveAndFlush(row);
        audit.record(actor.id(), id == null ? "PED_CLASS_CREATED" : "PED_CLASS_UPDATED", "SchoolClass", row.id, null);
        return classView(row);
    }
    @Transactional(readOnly=true)
    public List<Summary> list(UUID classId, Integer year, Integer month, String status, UUID teacherId, AuthenticatedUser actor) {
        classroom(classId, actor, false);
        if (month != null && (year == null || month < 1 || month > 12)) throw DomainException.unprocessable("Período inválido.");
        if (year != null && (year < 1900 || year > 9998)) throw DomainException.unprocessable("Ano inválido.");
        if (status != null && !Set.of("DRAFT", "FINALIZED").contains(status)) throw DomainException.unprocessable("Status inválido.");
        LocalDate start = year == null ? null : LocalDate.of(year, month == null ? 1 : month, 1);
        LocalDate end = start == null ? null : month == null ? start.plusYears(1) : start.plusMonths(1);
        return plans.findByClassIdOrderByWeekStartDesc(classId).stream()
                .filter(p -> start == null || (!p.weekStart.plusDays(4).isBefore(start) && p.weekStart.isBefore(end)))
                .filter(p -> status == null || status.equals(p.status))
                .filter(p -> teacherId == null || p.teachers.stream().anyMatch(t -> t.teacherId.equals(teacherId)))
                .map(p -> new Summary(p.id, p.classId, p.weekStart, p.weekStart.plusDays(4), p.theme, p.status, teacherViews(p), p.rowVersion)).toList();
    }
    @Transactional
    public PlanView create(UUID classId, LocalDate monday, AuthenticatedUser actor) {
        var classroom = classroom(classId, actor, true); writable(classroom);
        if (monday == null || monday.getDayOfWeek() != DayOfWeek.MONDAY || monday.getYear() < 1900 || monday.getYear() > 9998)
            throw DomainException.unprocessable("Selecione uma segunda-feira entre 1900 e 9998.");
        if (plans.existsByClassIdAndWeekStart(classId, monday)) throw DomainException.conflict("Esta turma já possui planejamento para a semana.");
        var p = new WeeklyPlanning(classId, monday, actor.id());
        identity.displayNames(classroom.teacherIds).entrySet().stream().sorted(Map.Entry.comparingByValue())
                .filter(t -> identity.activeUserHasAnyRole(t.getKey(), Set.of(Role.REQUESTER)))
                .forEach(t -> p.teachers.add(new PlanningTeacher(t.getKey(), t.getValue())));
        for (int i=0; i<5; i++) p.days.add(new PlanningDay(monday.plusDays(i), new DayInput(false, null, null, null, null, null)));
        plans.saveAndFlush(p);
        audit.record(actor.id(), "PED_PLANNING_CREATED", "WeeklyPlanning", p.id, null);
        return view(p, classroom);
    }
    @Transactional(readOnly=true)
    public PlanView get(UUID id, AuthenticatedUser actor) {
        var p = required(id); return view(p, classroom(p.classId, actor, false));
    }
    @Transactional
    public PlanView edit(UUID id, EditInput input, AuthenticatedUser actor) {
        var p = required(id); var c = classroom(p.classId, actor, true); writable(c); version(p.rowVersion, input.version());
        if (!p.status.equals("DRAFT")) throw DomainException.unprocessable("Reabra o planejamento antes de editar.");
        var old = new HashMap<UUID, PlanningTeacher>(); p.teachers.forEach(t -> old.put(t.teacherId, t));
        var selected = new ArrayList<PlanningTeacher>();
        for (var teacher : new LinkedHashSet<>(input.teacherIds())) {
            if (old.containsKey(teacher)) selected.add(old.get(teacher));
            else {
                if (!c.teacherIds.contains(teacher) || !identity.activeUserHasAnyRole(teacher, Set.of(Role.REQUESTER)))
                    throw DomainException.unprocessable("Selecione professoras ativas alocadas na turma.");
                selected.add(new PlanningTeacher(teacher, identity.find(teacher).displayName()));
            }
        }
        p.theme = input.theme(); p.teachers.clear(); p.teachers.addAll(selected); p.days.clear();
        for (int i=0; i<5; i++) p.days.add(new PlanningDay(p.weekStart.plusDays(i), input.days().get(i)));
        p.touch(); plans.flush(); audit.record(actor.id(), "PED_PLANNING_UPDATED", "WeeklyPlanning", id, null);
        return view(p, c);
    }
    @Transactional
    public PlanView transition(UUID id, long expected, boolean finalize, AuthenticatedUser actor) {
        var p = required(id); var c = classroom(p.classId, actor, true); writable(c); version(p.rowVersion, expected);
        if (finalize && !p.status.equals("DRAFT") || !finalize && !p.status.equals("FINALIZED"))
            throw DomainException.unprocessable("O planejamento não está no estado esperado.");
        if (finalize) {
            if (blank(p.theme) || p.teachers.isEmpty()) throw DomainException.unprocessable("Informe o tema e ao menos uma professora responsável.");
            for (var day : p.days) {
                if (day.noClass ? blank(day.reason) : blank(day.proposal) || blank(day.objectives) || blank(day.development) || blank(day.resources))
                    throw DomainException.unprocessable("Preencha todos os campos de " + day.activityDate + " ou marque Sem aula e informe o motivo.");
            }
        }
        p.status = finalize ? "FINALIZED" : "DRAFT"; p.touch(); plans.flush();
        audit.record(actor.id(), finalize ? "PED_PLANNING_FINALIZED" : "PED_PLANNING_REOPENED", "WeeklyPlanning", id, null);
        return view(p, c);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private WeeklyPlanning required(UUID id) { return plans.findById(id).orElseThrow(() -> DomainException.notFound("Planejamento não encontrado.")); }
    private ClassView classView(SchoolClass c) {
        var names = identity.displayNames(c.teacherIds);
        return new ClassView(c.id, c.name, c.archived, names.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .map(t -> new Teacher(t.getKey(), t.getValue())).toList(), c.rowVersion);
    }
    private List<Teacher> teacherViews(WeeklyPlanning p) { return p.teachers.stream().map(t -> new Teacher(t.teacherId, t.displayName)).toList(); }
    private PlanView view(WeeklyPlanning p, SchoolClass c) {
        return new PlanView(p.id, p.classId, c.name, c.archived, p.weekStart, p.weekStart.plusDays(4), p.theme, p.status,
                teacherViews(p), p.days.stream().map(d -> new DayView(d.activityDate, d.noClass, d.reason, d.proposal, d.objectives, d.development, d.resources)).toList(),
                p.rowVersion, p.createdAt, p.updatedAt);
    }
}
