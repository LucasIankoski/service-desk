package com.centralservicos.pedagogical;
import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static com.centralservicos.pedagogical.PedagogicalViews.*;
@RestController
@RequestMapping("/api/v1/pedagogical/aee/{aeeId}/classes/{classId}/students/{studentId}")
class AeePlanController {
    private final PedagogicalService service;
    private final AeeService access;
    private final CurrentUser user;
    AeePlanController(PedagogicalService service,AeeService access,CurrentUser user) { this.service=service; this.access=access; this.user=user; }
    @ModelAttribute void context(@PathVariable UUID aeeId,@PathVariable UUID classId,@PathVariable UUID studentId) { access.context(aeeId,classId,studentId,user.required()); }
    @GetMapping("/plans") List<Summary> plans(@PathVariable UUID studentId, @PathVariable UUID classId,
            @RequestParam(required=false) Integer year, @RequestParam(required=false) Integer month,
            @RequestParam(required=false) String status, @RequestParam(required=false) UUID teacherId) {
        return service.list(classId, year, month, status, teacherId, user.required(), studentId);
    }
    @PostMapping("/plans") @ResponseStatus(HttpStatus.CREATED)
    PlanView create(@PathVariable UUID studentId, @PathVariable UUID classId, @Valid @RequestBody CreateInput input) { return service.create(classId, input.weekStart(), user.required(), studentId); }
    @DeleteMapping("/plans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID studentId, @PathVariable UUID id, @RequestParam long version) { service.delete(id, version, user.required(), studentId); }
    @GetMapping("/plans/{id}") PlanView get(@PathVariable UUID studentId, @PathVariable UUID id) { return service.get(id, user.required(), studentId); }
    @PutMapping("/plans/{id}") PlanView edit(@PathVariable UUID studentId, @PathVariable UUID id, @Valid @RequestBody EditInput input) { return service.edit(id, input, user.required(), studentId); }
    @PostMapping("/plans/{id}/finalize") PlanView finalizePlan(@PathVariable UUID studentId, @PathVariable UUID id, @Valid @RequestBody VersionInput input) {
        return service.transition(id, input.version(), true, user.required(), studentId);
    }
    @PostMapping("/plans/{id}/reopen") PlanView reopen(@PathVariable UUID studentId, @PathVariable UUID id, @Valid @RequestBody VersionInput input) {
        return service.transition(id, input.version(), false, user.required(), studentId);
    }
}
