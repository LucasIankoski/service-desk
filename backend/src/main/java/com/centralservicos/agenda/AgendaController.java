package com.centralservicos.agenda;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agenda/items")
class AgendaController {

    private final AgendaService agenda;
    private final CurrentUser currentUser;

    AgendaController(AgendaService agenda, CurrentUser currentUser) {
        this.agenda = agenda;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<AgendaItemView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return agenda.list(start, end, currentUser.required());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgendaItemView create(@Valid @RequestBody CreateAgendaItemRequest request) {
        return agenda.create(request.kind(), request.title(), request.description(), request.location(),
                request.assigneeId(), request.startAt(), request.endAt(), request.allDay(), currentUser.required());
    }

    @PatchMapping("/{id}")
    AgendaItemView update(@PathVariable UUID id, @Valid @RequestBody UpdateAgendaItemRequest request) {
        return agenda.update(id, request.title(), request.description(), request.location(), request.assigneeId(),
                request.startAt(), request.endAt(), request.allDay(), request.version(), currentUser.required());
    }

    @PatchMapping("/{id}/status")
    AgendaItemView changeStatus(@PathVariable UUID id, @Valid @RequestBody AgendaStatusRequest request) {
        return agenda.changeStatus(id, request.status(), request.version(), currentUser.required());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version) {
        agenda.delete(id, version, currentUser.required());
    }

    record CreateAgendaItemRequest(@NotNull AgendaItemKind kind,
                                   @NotBlank @Size(max = 160) String title,
                                   @Size(max = 4000) String description,
                                   @Size(max = 200) String location,
                                   UUID assigneeId,
                                   @NotNull Instant startAt,
                                   @NotNull Instant endAt,
                                   boolean allDay) {
    }

    record UpdateAgendaItemRequest(@NotBlank @Size(max = 160) String title,
                                   @Size(max = 4000) String description,
                                   @Size(max = 200) String location,
                                   UUID assigneeId,
                                   @NotNull Instant startAt,
                                   @NotNull Instant endAt,
                                   boolean allDay,
                                   long version) {
    }

    record AgendaStatusRequest(@NotNull AgendaItemStatus status, long version) {
    }
}
