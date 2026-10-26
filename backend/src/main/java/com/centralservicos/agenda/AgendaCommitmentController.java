package com.centralservicos.agenda;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/agenda/commitments")
class AgendaCommitmentController {
    private final AgendaOccurrenceService service;
    private final CurrentUser currentUser;
    AgendaCommitmentController(AgendaOccurrenceService service, CurrentUser currentUser) { this.service = service; this.currentUser = currentUser; }
    @GetMapping("/collaborators")
    List<com.centralservicos.identity.IdentityService.CollaboratorView> collaborators() { return service.collaborators(currentUser.required()); }
    @GetMapping
    List<AgendaOccurrenceView> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.list(start, end, currentUser.required(), DailyNoteKind.COMMITMENT);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    AgendaOccurrenceView create(@Valid @RequestBody CreateRequest input) {
        return service.create(input.date(), input.body(), currentUser.required(), DailyNoteKind.COMMITMENT, input.collaboratorId());
    }
    @PatchMapping("/{id}")
    AgendaOccurrenceView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest input) {
        return service.update(id, input.date(), input.body(), input.version(), currentUser.required(), DailyNoteKind.COMMITMENT, input.collaboratorId());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version) { service.delete(id, version, currentUser.required(), DailyNoteKind.COMMITMENT); }
    record CreateRequest(@NotNull LocalDate date, @NotBlank @Size(max=4000) String body, UUID collaboratorId) {}
    record UpdateRequest(@NotNull LocalDate date, @NotBlank @Size(max=4000) String body, @NotNull Long version, UUID collaboratorId) {}
}
