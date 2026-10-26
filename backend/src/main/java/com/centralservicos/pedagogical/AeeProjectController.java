package com.centralservicos.pedagogical;
import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static com.centralservicos.pedagogical.ProjectViews.*;
@RestController
@RequestMapping("/api/v1/pedagogical/aee/{aeeId}/classes/{classId}/students/{studentId}")
class AeeProjectController {
    private final ProjectService service;
    private final AeeService access;
    private final CurrentUser user;
    AeeProjectController(ProjectService service,AeeService access,CurrentUser user) { this.service=service; this.access=access; this.user=user; }
    @ModelAttribute void context(@PathVariable UUID aeeId,@PathVariable UUID classId,@PathVariable UUID studentId) { access.context(aeeId,classId,studentId,user.required()); }
    @GetMapping("/projects") List<Summary> list(@PathVariable UUID studentId, @PathVariable UUID classId,
            @RequestParam(required=false) Integer year, @RequestParam(required=false) Integer month,
            @RequestParam(required=false) String status, @RequestParam(required=false) UUID teacherId) {
        return service.list(classId, year, month, status, teacherId, user.required(), studentId);
    }
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED)
    View create(@PathVariable UUID studentId, @PathVariable UUID classId, @Valid @RequestBody CreateInput input) { return service.create(classId, input, user.required(), studentId); }
    @DeleteMapping("/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID studentId, @PathVariable UUID id, @RequestParam long version) { service.delete(id, version, user.required(), studentId); }
    @GetMapping("/projects/{id}") View get(@PathVariable UUID studentId, @PathVariable UUID id) { return service.get(id, user.required(), studentId); }
    @PutMapping("/projects/{id}") View edit(@PathVariable UUID studentId, @PathVariable UUID id, @Valid @RequestBody EditInput input) { return service.edit(id, input, user.required(), studentId); }
    @PostMapping("/projects/{id}/finalize") View finalizeProject(@PathVariable UUID studentId, @PathVariable UUID id, @Valid @RequestBody PedagogicalViews.VersionInput input) {
        return service.transition(id, input.version(), true, user.required(), studentId);
    }
    @PostMapping("/projects/{id}/reopen") View reopen(@PathVariable UUID studentId, @PathVariable UUID id, @Valid @RequestBody PedagogicalViews.VersionInput input) {
        return service.transition(id, input.version(), false, user.required(), studentId);
    }
}
