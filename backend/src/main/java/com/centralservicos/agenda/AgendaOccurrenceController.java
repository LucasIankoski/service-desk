package com.centralservicos.agenda;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agenda/occurrences")
class AgendaOccurrenceController {
    private final AgendaOccurrenceService service;
    private final CurrentUser currentUser;

    AgendaOccurrenceController(AgendaOccurrenceService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<AgendaOccurrenceView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.list(start, end, currentUser.required());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgendaOccurrenceView create(@Valid @RequestBody CreateRequest request) {
        return service.create(request.date(), request.body(), currentUser.required());
    }

    @PatchMapping("/{id}")
    AgendaOccurrenceView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, request.date(), request.body(), request.version(), currentUser.required());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version) {
        service.delete(id, version, currentUser.required());
    }

    record CreateRequest(@NotNull LocalDate date, @NotBlank @Size(max = 4000) String body) {}
    record UpdateRequest(@NotNull LocalDate date, @NotBlank @Size(max = 4000) String body, @NotNull Long version) {}
}
