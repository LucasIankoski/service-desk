package com.centralservicos.pedagogical;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static com.centralservicos.pedagogical.ActivityRecordViews.*;

@RestController
@RequestMapping("/api/v1/pedagogical")
class ActivityRecordController {
    private final ActivityRecordService service;
    private final CurrentUser user;
    ActivityRecordController(ActivityRecordService service, CurrentUser user) { this.service = service; this.user = user; }
    @GetMapping("/classes/{id}/records")
    Page list(@PathVariable UUID id, @RequestParam(required = false) Integer year,
              @RequestParam(required = false) Integer month, @RequestParam(defaultValue = "0") int page) {
        return service.list(id, year, month, page, user.required());
    }
    @PostMapping(value = "/classes/{id}/records", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    View create(@PathVariable UUID id, @Valid @RequestPart("metadata") Input input,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.create(id, input, files, user.required());
    }
    @GetMapping("/records/{id}") View get(@PathVariable UUID id) { return service.get(id, user.required()); }
    @PutMapping(value = "/records/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    View update(@PathVariable UUID id, @Valid @RequestPart("metadata") Input input,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.update(id, input, files, user.required());
    }
    @DeleteMapping("/records/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version) { service.delete(id, version, user.required()); }
    @GetMapping("/records/{id}/photos/{photoId}")
    ResponseEntity<Resource> photo(@PathVariable UUID id, @PathVariable UUID photoId) { return image(id, photoId, false); }
    @GetMapping("/records/{id}/photos/{photoId}/thumbnail")
    ResponseEntity<Resource> thumbnail(@PathVariable UUID id, @PathVariable UUID photoId) { return image(id, photoId, true); }
    private ResponseEntity<Resource> image(UUID id, UUID photoId, boolean thumbnail) {
        var file = service.image(id, photoId, thumbnail, user.required());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.VARY, "Cookie")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.mediaType())).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.resource());
    }
}
