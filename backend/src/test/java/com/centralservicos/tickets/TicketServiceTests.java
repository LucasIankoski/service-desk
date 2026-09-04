package com.centralservicos.tickets;

import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.shared.CommentVisibility;
import com.centralservicos.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TicketServiceTests {

    @Autowired TicketService tickets;
    @Autowired CategoryService categories;
    @Autowired IdentityService identity;

    @Test
    void requesterCreatesAndSeesOnlyOwnTicket() {
        var requester = user("requester", Role.REQUESTER);
        var otherRequester = user("other", Role.REQUESTER);

        var ticket = tickets.create("Impressora sem toner", "A impressora da recepção parou.", null,
                List.of(), requester);

        assertThat(ticket.publicNumber()).startsWith("SD-");
        assertThat(tickets.detail(ticket.id(), requester).requesterId()).isEqualTo(requester.id());
        assertThatThrownBy(() -> tickets.detail(ticket.id(), otherRequester))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("indisponível");
    }

    @Test
    void categoryIsRequiredBeforeWorkStarts() {
        var requester = user("requester-work", Role.REQUESTER);
        var agent = user("agent-work", Role.AGENT);
        var ticket = tickets.create("Acesso ao sistema", "Preciso acessar o módulo financeiro.", null,
                List.of(), requester);
        ticket = tickets.assign(ticket.id(), agent.id(), ticket.version(), agent);

        var assigned = ticket;
        assertThat(assigned.status()).isEqualTo(TicketStatus.OPEN);
        assertThatThrownBy(() -> tickets.transition(assigned.id(), TicketStatus.IN_PROGRESS,
                assigned.version(), agent))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("categoria");
    }

    @Test
    void categoryCannotBeRenamedToAnExistingName() {
        var admin = user("admin-category", Role.ADMIN);
        var first = categories.create("Aplicativos " + UUID.randomUUID(), admin.id());
        var second = categories.create("Equipamentos " + UUID.randomUUID(), admin.id());

        assertThatThrownBy(() -> categories.update(second.id(), first.name().toUpperCase(), true, admin.id()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Já existe uma categoria");
    }

    @Test
    void requesterCannotCreateInternalNote() {
        var requester = user("requester-note", Role.REQUESTER);
        var ticket = tickets.create("Atualizar cadastro", "Meu telefone mudou.", null, List.of(), requester);

        assertThatThrownBy(() -> tickets.comment(ticket.id(), "nota privada",
                CommentVisibility.INTERNAL, List.of(), requester))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Notas internas");
    }

    @Test
    void unsafeAttachmentIsRejectedBeforePersistence() {
        var requester = user("requester-file", Role.REQUESTER);
        var html = new MockMultipartFile("files", "payload.html", "text/html",
                "<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> tickets.create("Arquivo suspeito", "Segue anexo.", null, List.of(html),
                requester))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Tipo de arquivo");
    }

    @Test
    void agentCanClassifyAndResolveTicket() {
        var requester = user("requester-flow", Role.REQUESTER);
        var agent = user("agent-flow", Role.AGENT);
        var category = categories.create("Infraestrutura " + UUID.randomUUID(), agent.id());
        var ticket = tickets.create("VPN instável", "A conexão cai a cada 10 minutos.", null,
                List.of(), requester);

        ticket = tickets.assign(ticket.id(), agent.id(), ticket.version(), agent);
        ticket = tickets.classify(ticket.id(), category.id(), Priority.HIGH, null, ticket.version(), agent);
        ticket = tickets.transition(ticket.id(), TicketStatus.IN_PROGRESS, ticket.version(), agent);
        ticket = tickets.transition(ticket.id(), TicketStatus.RESOLVED, ticket.version(), agent);

        assertThat(ticket.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.priority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void requesterCanReopenARecentlyResolvedTicket() {
        var requester = user("requester-reopen", Role.REQUESTER);
        var agent = user("agent-reopen", Role.AGENT);
        var category = categories.create("Rede " + UUID.randomUUID(), agent.id());
        var ticket = tickets.create("VPN bloqueada", "A VPN não conecta desde cedo.", null, List.of(), requester);
        ticket = tickets.assign(ticket.id(), agent.id(), ticket.version(), agent);
        ticket = tickets.classify(ticket.id(), category.id(), Priority.NORMAL, null, ticket.version(), agent);
        ticket = tickets.transition(ticket.id(), TicketStatus.IN_PROGRESS, ticket.version(), agent);
        ticket = tickets.transition(ticket.id(), TicketStatus.RESOLVED, ticket.version(), agent);

        var reopened = tickets.transition(ticket.id(), TicketStatus.IN_PROGRESS, ticket.version(), requester);

        assertThat(reopened.status()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void requesterCannotRefreshTheReopenWindowWithSameStatus() {
        var requester = user("requester-same-status", Role.REQUESTER);
        var ticket = tickets.create("Status indevido", "Tentativa de manter o mesmo estado.", null,
                List.of(), requester);

        assertThatThrownBy(() -> tickets.transition(ticket.id(), TicketStatus.OPEN, ticket.version(), requester))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("já está");
    }

    @Test
    void internalNotesAndTheirAttachmentsStayHiddenFromRequester() {
        var requester = user("requester-private", Role.REQUESTER);
        var agent = user("agent-private", Role.AGENT);
        var ticket = tickets.create("Análise interna", "Há dados para o atendimento.", null,
                List.of(), requester);
        var text = new MockMultipartFile("files", "evidence.txt", "text/plain", "internal".getBytes());

        var agentView = tickets.comment(ticket.id(), "Nota apenas da equipe", CommentVisibility.INTERNAL,
                List.of(text), agent);
        var requesterView = tickets.detail(ticket.id(), requester);

        assertThat(agentView.comments()).hasSize(1);
        assertThat(agentView.comments().getFirst().attachments()).hasSize(1);
        assertThat(requesterView.comments()).isEmpty();
    }

    @Test
    void renamedZipIsNotAcceptedAsOfficeDocument() throws Exception {
        var requester = user("requester-archive", Role.REQUESTER);
        var fakeOffice = new MockMultipartFile("files", "archive.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", fakeZip());

        assertThatThrownBy(() -> tickets.create("Arquivo renomeado", "O ZIP não é um DOCX válido.", null,
                List.of(fakeOffice), requester))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("documento Office válido");
    }

    private byte[] fakeZip() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("not an office file".getBytes());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private AuthenticatedUser user(String prefix, Role role) {
        var email = prefix + "-" + UUID.randomUUID() + "@example.test";
        var created = identity.create(email, prefix, Set.of(role), null).user();
        return new AuthenticatedUser(created.id(), created.email(), created.displayName(),
                "{noop}not-used", created.roles(), true, false);
    }
}
