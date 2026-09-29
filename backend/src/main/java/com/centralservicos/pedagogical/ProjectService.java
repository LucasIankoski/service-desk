package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.centralservicos.pedagogical.ProjectViews.*;

@Service
class ProjectService {
    private final ProjectRepository projects;
    private final PedagogicalService access;
    private final IdentityService identity;
    private final AuditService audit;
    private final Validator validator;
    ProjectService(ProjectRepository projects, PedagogicalService access, IdentityService identity, AuditService audit, Validator validator) {
        this.projects = projects; this.access = access; this.identity = identity; this.audit = audit; this.validator = validator;
    }
    private void validate(Object input) {
        if (input == null || !validator.validate(input).isEmpty()) throw DomainException.unprocessable("Confira os campos obrigatórios e os limites de preenchimento.");
    }
    private void period(LocalDate start, LocalDate end) {
        if (start.getYear() < 1900 || end.getYear() > 9998 || end.isBefore(start))
            throw DomainException.unprocessable("Informe um período válido entre 1900 e 9998, com término igual ou posterior ao início.");
    }
    private void writable(SchoolClass c) {
        if (c.archived) throw DomainException.unprocessable("Reative a turma antes de alterar seus projetos.");
    }
    private void version(PedagogicalProject p, Long expected) {
        if (!Objects.equals(p.rowVersion, expected)) throw DomainException.conflict("Este registro mudou. Seu texto foi preservado; consulte a versão atual antes de salvar novamente.");
    }
    private PedagogicalProject required(UUID id) {
        return projects.findById(id).orElseThrow(() -> DomainException.notFound("Projeto não encontrado."));
    }
    @Transactional(readOnly=true)
    public List<Summary> list(UUID classId, Integer year, Integer month, String status, UUID teacherId, AuthenticatedUser actor) {
        access.classroom(classId, actor, false);
        if (year != null && (year < 1900 || year > 9998) || month != null && (year == null || month < 1 || month > 12))
            throw DomainException.unprocessable("Período inválido.");
        if (status != null && !Set.of("DRAFT", "FINALIZED").contains(status)) throw DomainException.unprocessable("Status inválido.");
        var start = year == null ? null : LocalDate.of(year, month == null ? 1 : month, 1);
        var end = start == null ? null : month == null ? start.plusYears(1) : start.plusMonths(1);
        return projects.findByClassIdOrderByStartDateDescCreatedAtDescIdAsc(classId).stream()
                .filter(p -> start == null || !p.endDate.isBefore(start) && p.startDate.isBefore(end))
                .filter(p -> status == null || status.equals(p.status))
                .filter(p -> teacherId == null || p.teachers.stream().anyMatch(t -> t.teacherId.equals(teacherId)))
                .map(p -> new Summary(p.id, p.classId, p.title, p.startDate, p.endDate, p.status, teachers(p), p.rowVersion)).toList();
    }
    @Transactional
    public View create(UUID classId, CreateInput input, AuthenticatedUser actor) {
        var c = access.classroom(classId, actor, true); writable(c); validate(input); period(input.startDate(), input.endDate());
        var p = new PedagogicalProject(classId, input, actor.id());
        identity.displayNames(c.teacherIds).entrySet().stream().sorted(Map.Entry.comparingByValue())
                .filter(t -> identity.activeUserHasAnyRole(t.getKey(), Set.of(Role.REQUESTER)))
                .forEach(t -> p.teachers.add(new PlanningTeacher(t.getKey(), t.getValue())));
        projects.saveAndFlush(p); audit.record(actor.id(), "PED_PROJECT_CREATED", "PedagogicalProject", p.id, null);
        return view(p, c);
    }
    @Transactional(readOnly=true)
    public View get(UUID id, AuthenticatedUser actor) { var p = required(id); return view(p, access.classroom(p.classId, actor, false)); }
    @Transactional
    public View edit(UUID id, EditInput input, AuthenticatedUser actor) {
        var p = required(id); var c = access.classroom(p.classId, actor, true); writable(c); validate(input); version(p, input.version());
        if (!p.status.equals("DRAFT")) throw DomainException.unprocessable("Reabra o projeto antes de editar.");
        period(input.startDate(), input.endDate());
        if (input.generalObjective().length() > 40000 || input.conclusion().length() > 40000
                || input.activities().stream().anyMatch(a -> a.description().length() > 40000))
            throw DomainException.unprocessable("Cada texto formatado pode conter até 40.000 caracteres.");
        for (var a : input.activities()) if (a.date() != null && (a.date().isBefore(input.startDate()) || a.date().isAfter(input.endDate())))
            throw DomainException.unprocessable("As datas das atividades devem pertencer ao período do projeto.");
        var previous = new HashMap<UUID, PlanningTeacher>(); p.teachers.forEach(t -> previous.put(t.teacherId, t));
        var selected = new ArrayList<PlanningTeacher>();
        for (var idTeacher : new LinkedHashSet<>(input.teacherIds())) {
            if (previous.containsKey(idTeacher)) selected.add(previous.get(idTeacher));
            else {
                if (!c.teacherIds.contains(idTeacher) || !identity.activeUserHasAnyRole(idTeacher, Set.of(Role.REQUESTER)))
                    throw DomainException.unprocessable("Selecione professoras ativas alocadas na turma.");
                selected.add(new PlanningTeacher(idTeacher, identity.find(idTeacher).displayName()));
            }
        }
        p.title = input.title().trim(); p.startDate = input.startDate(); p.endDate = input.endDate(); p.ageRange = input.ageRange();
        p.generalObjective = input.generalObjective(); p.conclusion = input.conclusion();
        p.teachers.clear(); p.teachers.addAll(selected);
        p.specificObjectives.clear(); input.specificObjectives().stream().map(String::strip).filter(s -> !s.isBlank()).forEach(p.specificObjectives::add);
        p.activities.clear(); input.activities().stream().sorted(Comparator.comparing(Activity::date, Comparator.nullsLast(Comparator.naturalOrder()))).map(ProjectActivity::new).forEach(p.activities::add);
        p.touch(); projects.flush(); audit.record(actor.id(), "PED_PROJECT_UPDATED", "PedagogicalProject", id, null);
        return view(p, c);
    }
    @Transactional
    public View transition(UUID id, long expected, boolean finalize, AuthenticatedUser actor) {
        var p = required(id); var c = access.classroom(p.classId, actor, true); writable(c); version(p, expected);
        if (!p.status.equals(finalize ? "DRAFT" : "FINALIZED")) throw DomainException.unprocessable("O projeto não está no estado esperado.");
        if (finalize && (blank(p.ageRange) || p.teachers.isEmpty() || p.generalObjective.blank() || p.conclusion.blank()
                || p.specificObjectives.isEmpty() || p.specificObjectives.stream().anyMatch(this::blank)
                || p.activities.isEmpty() || p.activities.stream().anyMatch(a -> a.description.blank() || a.activityDate != null && blank(a.title))))
            throw DomainException.unprocessable("Preencha a faixa etária, responsáveis, objetivo geral, objetivos específicos, desenvolvimento e conclusão antes de finalizar.");
        p.status = finalize ? "FINALIZED" : "DRAFT"; p.touch(); projects.flush();
        audit.record(actor.id(), finalize ? "PED_PROJECT_FINALIZED" : "PED_PROJECT_REOPENED", "PedagogicalProject", id, null);
        return view(p, c);
    }
    @Transactional
    public void delete(UUID id, long expected, AuthenticatedUser actor) {
        if (!access.administrative(actor)) throw DomainException.forbidden("Esta ação exige perfil administrativo.");
        var p = required(id);
        writable(access.classroom(p.classId, actor, true));
        version(p, expected);
        projects.delete(p); projects.flush();
        audit.record(actor.id(), "PED_PROJECT_DELETED", "PedagogicalProject", id, null);
    }
    private List<PedagogicalViews.Teacher> teachers(PedagogicalProject p) {
        return p.teachers.stream().map(t -> new PedagogicalViews.Teacher(t.teacherId, t.displayName)).toList();
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private View view(PedagogicalProject p, SchoolClass c) {
        return new View(p.id, p.classId, c.name, c.archived, p.title, p.startDate, p.endDate, Objects.toString(p.ageRange, ""), p.status,
                teachers(p), p.generalObjective, p.specificObjectives.stream().map(s -> Objects.toString(s, "")).toList(), p.activities.stream().map(ProjectActivity::view).toList(),
                p.conclusion, p.rowVersion, p.createdAt, p.updatedAt);
    }
}
