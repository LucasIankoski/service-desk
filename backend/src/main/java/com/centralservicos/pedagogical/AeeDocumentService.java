package com.centralservicos.pedagogical;

import com.centralservicos.attachments.*;
import com.centralservicos.audit.AuditService;
import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.settings.SettingsService;
import com.centralservicos.shared.DomainException;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
class AeeDocumentService {
    record View(UUID id,String filename,long size,UUID authorId,String authorName,Instant createdAt,long version,boolean canDelete) {}
    record Page(List<View> items,boolean canUpload,int attachmentLimitMb) {}
    private final AeeDocumentRepository documents;
    private final AeeService access;
    private final AttachmentStorage storage;
    private final PhotoCleanupService cleanup;
    private final SettingsService settings;
    private final AuditService audit;
    AeeDocumentService(AeeDocumentRepository documents,AeeService access,AttachmentStorage storage,PhotoCleanupService cleanup,SettingsService settings,AuditService audit) {
        this.documents=documents; this.access=access; this.storage=storage; this.cleanup=cleanup; this.settings=settings; this.audit=audit;
    }
    private boolean owner(AeeDocument d,AuthenticatedUser actor) { return d.authorId.equals(actor.id())||access.admin(actor); }
    private AeeDocument required(UUID studentId,UUID id) {
        return documents.findById(id).filter(d -> d.studentId.equals(studentId)).orElseThrow(() -> DomainException.notFound("Documento não encontrado neste aluno."));
    }
    private View view(AeeDocument d,boolean editable,AuthenticatedUser actor) { return new View(d.id,d.filename,d.size,d.authorId,d.authorName,d.createdAt,d.rowVersion,editable&&owner(d,actor)); }
    @Transactional(readOnly=true)
    public Page list(UUID studentId,AuthenticatedUser actor) {
        var c=access.contentClass(studentId,actor,false);
        boolean editable=!c.archived && (access.admin(actor)||c.teacherIds.contains(actor.id()));
        return new Page(documents.findByStudentIdOrderByCreatedAtDescIdAsc(studentId).stream().map(d -> view(d,editable,actor)).toList(),editable,settings.ticketPolicy().attachmentLimitMb());
    }
    @Transactional
    public View upload(UUID studentId,MultipartFile file,AuthenticatedUser actor) {
        access.contentClass(studentId,actor,true);
        if (file==null||file.isEmpty()) throw DomainException.unprocessable("Selecione um arquivo PDF.");
        int limit=settings.ticketPolicy().attachmentLimitMb();
        if (file.getSize()>limit*1024L*1024L) throw new DomainException(HttpStatus.PAYLOAD_TOO_LARGE,"O PDF excede o limite de "+limit+" MiB.");
        var name=Objects.toString(file.getOriginalFilename(),"").replace('\\','/'); name=name.substring(name.lastIndexOf('/')+1).strip();
        if (name.length()>255||name.chars().anyMatch(Character::isISOControl)||!name.toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw DomainException.unprocessable("Selecione um arquivo com extensão .pdf e nome válido.");
        byte[] bytes;
        try { bytes=file.getBytes(); } catch(IOException e) { throw DomainException.unprocessable("Não foi possível ler o arquivo."); }
        if (bytes.length<5||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")||!new Tika().detect(bytes).equals("application/pdf"))
            throw DomainException.unprocessable("A assinatura do arquivo não corresponde a um PDF.");
        var d=new AeeDocument(); d.studentId=studentId; d.filename=name; d.authorId=actor.id(); d.authorName=actor.displayName(); d.size=bytes.length;
        d.storageKey="aee/"+studentId+"/"+d.id+".pdf";
        cleanup.stage(List.of(d.storageKey));
        try { storage.store(d.storageKey,bytes); } catch(IOException e) { throw new DomainException(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível salvar o PDF. Tente novamente."); }
        documents.saveAndFlush(d); audit.record(actor.id(),"AEE_DOCUMENT_CREATED","AeeDocument",d.id,null);
        return view(d,true,actor);
    }
    @Transactional(readOnly=true)
    public StoredResource load(UUID studentId,UUID id,AuthenticatedUser actor) {
        var d=required(studentId,id); access.contentClass(studentId,actor,false);
        try { return storage.load(d.storageKey,"application/pdf",d.filename); } catch(IOException e) { throw DomainException.notFound("Não foi possível carregar o PDF."); }
    }
    @Transactional
    public void delete(UUID studentId,UUID id,long version,AuthenticatedUser actor) {
        var d=required(studentId,id); access.contentClass(studentId,actor,true);
        if (!owner(d,actor)) throw DomainException.forbidden("Somente a autora e a administração podem excluir este documento.");
        if (!Objects.equals(d.rowVersion,version)) throw DomainException.conflict("Este documento mudou. Atualize a lista antes de excluir.");
        cleanup.removedKey(d.storageKey); documents.delete(d); documents.flush();
        audit.record(actor.id(),"AEE_DOCUMENT_DELETED","AeeDocument",d.id,null);
    }
}
