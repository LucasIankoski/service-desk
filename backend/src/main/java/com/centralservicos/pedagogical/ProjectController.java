package com.centralservicos.pedagogical;
import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.centralservicos.pedagogical.ProjectViews.*;

@RestController
@RequestMapping("/api/v1/pedagogical")
class ProjectController {
    private final ProjectService service;
    private final CurrentUser user;
    ProjectController(ProjectService service, CurrentUser user) { this.service = service; this.user = user; }
    @GetMapping("/classes/{id}/projects") List<Summary> list(@PathVariable UUID id,
            @RequestParam(required=false) Integer year, @RequestParam(required=false) Integer month,
            @RequestParam(required=false) String status, @RequestParam(required=false) UUID teacherId) {
        return service.list(id, year, month, status, teacherId, user.required());
    }
    @PostMapping("/classes/{id}/projects") @ResponseStatus(HttpStatus.CREATED)
    View create(@PathVariable UUID id, @Valid @RequestBody CreateInput input) { return service.create(id, input, user.required()); }
    @DeleteMapping("/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version) { service.delete(id, version, user.required()); }
    @GetMapping("/projects/{id}") View get(@PathVariable UUID id) { return service.get(id, user.required()); }
    @PutMapping("/projects/{id}") View edit(@PathVariable UUID id, @Valid @RequestBody EditInput input) { return service.edit(id, input, user.required()); }
    @PostMapping("/projects/{id}/finalize") View finalizeProject(@PathVariable UUID id, @Valid @RequestBody PedagogicalViews.VersionInput input) {
        return service.transition(id, input.version(), true, user.required());
    }
    @PostMapping("/projects/{id}/reopen") View reopen(@PathVariable UUID id, @Valid @RequestBody PedagogicalViews.VersionInput input) {
        return service.transition(id, input.version(), false, user.required());
    }
}
