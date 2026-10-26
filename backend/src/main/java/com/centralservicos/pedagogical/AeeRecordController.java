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
@RequestMapping("/api/v1/pedagogical/aee/{aeeId}/classes/{classId}/students/{studentId}")
class AeeRecordController {
    private final ActivityRecordService service;
    private final AeeService access;
    private final CurrentUser user;
    AeeRecordController(ActivityRecordService service,AeeService access,CurrentUser user) { this.service=service; this.access=access; this.user=user; }
    @ModelAttribute void context(@PathVariable UUID aeeId,@PathVariable UUID classId,@PathVariable UUID studentId) { access.context(aeeId,classId,studentId,user.required()); }
    @GetMapping("/records")
    Page list(@PathVariable UUID studentId, @PathVariable UUID classId, @RequestParam(required = false) Integer year,
              @RequestParam(required = false) Integer month, @RequestParam(defaultValue = "0") int page) {
        return service.list(classId, year, month, page, user.required(), studentId);
    }
    @PostMapping(value = "/records", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    View create(@PathVariable UUID studentId, @PathVariable UUID classId, @Valid @RequestPart("metadata") Input input,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.create(classId, input, files, user.required(), studentId);
    }
    @GetMapping("/records/{id}") View get(@PathVariable UUID studentId, @PathVariable UUID id) { return service.get(id, user.required(), studentId); }
    @PutMapping(value = "/records/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    View update(@PathVariable UUID studentId, @PathVariable UUID id, @Valid @RequestPart("metadata") Input input,
                @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return service.update(id, input, files, user.required(), studentId);
    }
    @DeleteMapping("/records/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID studentId, @PathVariable UUID id, @RequestParam long version) { service.delete(id, version, user.required(), studentId); }
    @GetMapping("/records/{id}/photos/{photoId}")
    ResponseEntity<Resource> photo(@PathVariable UUID studentId, @PathVariable UUID id, @PathVariable UUID photoId) { return image(studentId, id, photoId, false); }
    @GetMapping("/records/{id}/photos/{photoId}/thumbnail")
    ResponseEntity<Resource> thumbnail(@PathVariable UUID studentId, @PathVariable UUID id, @PathVariable UUID photoId) { return image(studentId, id, photoId, true); }
    private ResponseEntity<Resource> image(UUID studentId, UUID id, UUID photoId, boolean thumbnail) {
        var file = service.image(id, photoId, thumbnail, user.required(), studentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.VARY, "Cookie")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(file.mediaType())).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build().toString())
                .body(file.resource());
    }
}
