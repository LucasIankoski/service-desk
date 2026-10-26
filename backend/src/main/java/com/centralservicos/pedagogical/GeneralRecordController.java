package com.centralservicos.pedagogical;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static com.centralservicos.pedagogical.GeneralRecordViews.*;

@RestController
@RequestMapping("/api/v1/records")
class GeneralRecordController {
    private final ActivityRecordService service;
    private final CurrentUser user;
    GeneralRecordController(ActivityRecordService service, CurrentUser user) { this.service = service; this.user = user; }
    @GetMapping
    Page list(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
              @RequestParam(defaultValue = "0") int page) { return service.listGeneral(year, month, page, user.required()); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    View create(@Valid @RequestPart("metadata") Input input,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.createGeneral(input.recordInput(), files, user.required());
    }
    @GetMapping("/{id}") View get(@PathVariable UUID id) { return service.getGeneral(id, user.required()); }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    View update(@PathVariable UUID id, @Valid @RequestPart("metadata") Input input,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.updateGeneral(id, input.recordInput(), files, user.required());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @RequestParam long version) { service.deleteGeneral(id, version, user.required()); }
    @GetMapping("/{id}/photos/{photoId}")
    ResponseEntity<Resource> photo(@PathVariable UUID id, @PathVariable UUID photoId) { return image(id, photoId, false); }
    @GetMapping("/{id}/photos/{photoId}/thumbnail")
    ResponseEntity<Resource> thumbnail(@PathVariable UUID id, @PathVariable UUID photoId) { return image(id, photoId, true); }
    private ResponseEntity<Resource> image(UUID id, UUID photoId, boolean thumbnail) {
        var file = service.generalImage(id, photoId, thumbnail, user.required());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.VARY, "Cookie")
                .header("X-Content-Type-Options", "nosniff").contentType(MediaType.parseMediaType(file.mediaType()))
                .contentLength(file.size()).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.resource());
    }
}
