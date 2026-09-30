package com.centralservicos.pedagogical;
import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.centralservicos.pedagogical.PedagogicalViews.*;
@RestController
@RequestMapping("/api/v1/pedagogical")
class PedagogicalController {
    private final PedagogicalService service;
    private final CurrentUser user;
    PedagogicalController(PedagogicalService service, CurrentUser user) { this.service = service; this.user = user; }
    @GetMapping("/classes") List<ClassView> classes() { return service.classes(user.required()); }
    @GetMapping("/teachers") List<Teacher> teachers() { return service.teachers(user.required()); }
    @GetMapping("/classes/{id}") ClassView classroom(@PathVariable UUID id) { return service.getClass(id, user.required()); }
    @PostMapping("/classes") @ResponseStatus(HttpStatus.CREATED)
    ClassView createClass(@Valid @RequestBody ClassInput input) { return service.saveClass(null, input, user.required()); }
    @PutMapping("/classes/{id}") ClassView updateClass(@PathVariable UUID id, @Valid @RequestBody ClassInput input) {
        return service.saveClass(id, input, user.required());
    }
    @GetMapping("/classes/{id}/plans") List<Summary> plans(@PathVariable UUID id,
            @RequestParam(required=false) Integer year, @RequestParam(required=false) Integer month,
            @RequestParam(required=false) String status, @RequestParam(required=false) UUID teacherId) {
        return service.list(id, year, month, status, teacherId, user.required());
    }
    @PostMapping("/classes/{id}/plans") @ResponseStatus(HttpStatus.CREATED)
    PlanView create(@PathVariable UUID id, @Valid @RequestBody CreateInput input) { return service.create(id, input.weekStart(), user.required()); }
    @DeleteMapping("/plans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version) { service.delete(id, version, user.required()); }
    @GetMapping("/plans/{id}") PlanView get(@PathVariable UUID id) { return service.get(id, user.required()); }
    @PutMapping("/plans/{id}") PlanView edit(@PathVariable UUID id, @Valid @RequestBody EditInput input) { return service.edit(id, input, user.required()); }
    @PostMapping("/plans/{id}/finalize") PlanView finalizePlan(@PathVariable UUID id, @Valid @RequestBody VersionInput input) {
        return service.transition(id, input.version(), true, user.required());
    }
    @PostMapping("/plans/{id}/reopen") PlanView reopen(@PathVariable UUID id, @Valid @RequestBody VersionInput input) {
        return service.transition(id, input.version(), false, user.required());
    }
}
