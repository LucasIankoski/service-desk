package com.centralservicos.pedagogical;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1/pedagogical/aee/{aeeId}/classes")
class AeeController {
    private final AeeService service;
    private final CurrentUser user;
    AeeController(AeeService service,CurrentUser user) { this.service=service; this.user=user; }
    @GetMapping List<AeeViews.Classroom> directory(@PathVariable UUID aeeId,@RequestParam(defaultValue="false") boolean archived) { return service.directory(aeeId,archived,user.required()); }
    @GetMapping("/{classId}/students") List<AeeViews.Student> students(@PathVariable UUID aeeId,@PathVariable UUID classId) { return service.students(aeeId,classId,user.required()); }
    @PostMapping("/{classId}/students") @ResponseStatus(HttpStatus.CREATED)
    AeeViews.Student create(@PathVariable UUID aeeId,@PathVariable UUID classId,@Valid @RequestBody AeeViews.StudentInput input) { return service.save(aeeId,classId,null,input,user.required()); }
    @PutMapping("/{classId}/students/{studentId}")
    AeeViews.Student update(@PathVariable UUID aeeId,@PathVariable UUID classId,@PathVariable UUID studentId,@Valid @RequestBody AeeViews.StudentInput input) { return service.save(aeeId,classId,studentId,input,user.required()); }
    @GetMapping("/{classId}/students/{studentId}")
    AeeViews.Context context(@PathVariable UUID aeeId,@PathVariable UUID classId,@PathVariable UUID studentId) { return service.context(aeeId,classId,studentId,user.required()); }
}

@RestController
@RequestMapping("/api/v1/pedagogical/aee/{aeeId}/classes/{classId}/students/{studentId}/documents")
class AeeDocumentController {
    private final AeeService access;
    private final AeeDocumentService service;
    private final CurrentUser user;
    AeeDocumentController(AeeService access,AeeDocumentService service,CurrentUser user) { this.access=access; this.service=service; this.user=user; }
    @ModelAttribute void context(@PathVariable UUID aeeId,@PathVariable UUID classId,@PathVariable UUID studentId) { access.context(aeeId,classId,studentId,user.required()); }
    @GetMapping AeeDocumentService.Page list(@PathVariable UUID studentId) { return service.list(studentId,user.required()); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    AeeDocumentService.View upload(@PathVariable UUID studentId,@RequestPart("file") MultipartFile file) { return service.upload(studentId,file,user.required()); }
    @GetMapping("/{id}") ResponseEntity<Resource> file(@PathVariable UUID studentId,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean download) {
        var file=service.load(studentId,id,user.required());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.VARY,"Cookie")
                .header("X-Content-Type-Options","nosniff").contentType(MediaType.APPLICATION_PDF).contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,(download?ContentDisposition.attachment():ContentDisposition.inline()).filename(file.filename(),StandardCharsets.UTF_8).build().toString()).body(file.resource());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID studentId,@PathVariable UUID id,@RequestParam long version) { service.delete(studentId,id,version,user.required()); }
}
