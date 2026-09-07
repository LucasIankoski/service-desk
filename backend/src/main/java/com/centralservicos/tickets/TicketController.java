package com.centralservicos.tickets;

import com.centralservicos.identity.CurrentUser;
import com.centralservicos.shared.CommentVisibility;
import com.centralservicos.shared.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
class TicketController {

    private final TicketService tickets;
    private final CurrentUser currentUser;

    TicketController(TicketService tickets, CurrentUser currentUser) {
        this.tickets = tickets;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<TicketSummaryView> list(@RequestParam(required = false) String number,
                                 @RequestParam(required = false) TicketStatus status,
                                 @RequestParam(required = false) UUID categoryId,
                                 @RequestParam(required = false) UUID assigneeId,
                                 Pageable pageable) {
        var filter = new TicketFilter(number, status, categoryId, assigneeId);
        return PageResponse.from(tickets.list(filter, pageable, currentUser.required()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    TicketDetailView create(@Valid @RequestPart("metadata") CreateTicketRequest request,
                            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return tickets.create(request.description(), request.categoryId(), files,
                currentUser.required());
    }

    @GetMapping("/{id}")
    TicketDetailView detail(@PathVariable UUID id) {
        return tickets.detail(id, currentUser.required());
    }

    @PatchMapping("/{id}/assignment")
    TicketDetailView assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest request) {
        return tickets.assign(id, request.assigneeId(), request.version(), currentUser.required());
    }

    @PatchMapping("/{id}/classification")
    TicketDetailView classify(@PathVariable UUID id, @Valid @RequestBody ClassifyRequest request) {
        return tickets.classify(id, request.categoryId(),
                request.version(), currentUser.required());
    }

    @PatchMapping("/{id}/status")
    TicketDetailView status(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return tickets.transition(id, request.status(), request.version(), currentUser.required());
    }

    @PostMapping(value = "/{id}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    TicketDetailView comment(@PathVariable UUID id, @Valid @RequestPart("metadata") CommentRequest request,
                             @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return tickets.comment(id, request.body(), request.visibility(), files, currentUser.required());
    }

    @GetMapping("/attachments/{id}")
    ResponseEntity<?> download(@PathVariable UUID id) {
        var file = tickets.download(id, currentUser.required());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, file.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.filename(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentLength(file.size())
                .body(file.resource());
    }

    record CreateTicketRequest(@NotBlank @Size(max = 8000) String description,
                               @NotNull UUID categoryId) {
    }

    record AssignRequest(@NotNull UUID assigneeId, long version) {
    }

    record ClassifyRequest(@NotNull UUID categoryId, long version) {
    }

    record StatusRequest(@NotNull TicketStatus status, long version) {
    }

    record CommentRequest(@NotBlank @Size(max = 4000) String body,
                          @NotNull CommentVisibility visibility) {
    }
}
