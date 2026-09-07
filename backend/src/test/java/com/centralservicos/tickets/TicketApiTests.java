package com.centralservicos.tickets;

import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.notifications.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class TicketApiTests {
    @Autowired WebApplicationContext context;
    @Autowired IdentityService identity;
    @Autowired TicketService tickets;
    @Autowired CategoryService categories;
    @Autowired NotificationService notifications;

    @Test
    void createsWithoutSubjectAndRequiresCategoryInHttpContract() throws Exception {
        var actor = account(Role.REQUESTER);
        var category = categories.create("API " + UUID.randomUUID(), actor.id());
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var metadata = new MockMultipartFile("metadata", "", "application/json",
                ("{\"description\":\"Descrição da demanda\",\"categoryId\":\"" + category.id() + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/tickets").file(metadata).with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryName").value(category.name()))
                .andExpect(jsonPath("$.subject").doesNotExist()).andExpect(jsonPath("$.priority").doesNotExist())
                .andExpect(jsonPath("$.dueAt").doesNotExist());
        var missing = new MockMultipartFile("metadata", "", "application/json",
                "{\"description\":\"Descrição da demanda\"}".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/tickets").file(missing).with(user(actor)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryFilteringClassificationPermissionsAndVersionsArePreserved() throws Exception {
        var requester = account(Role.REQUESTER);
        var agent = account(Role.AGENT);
        var first = categories.create("First " + UUID.randomUUID(), agent.id());
        var second = categories.create("Second " + UUID.randomUUID(), agent.id());
        var ticket = tickets.create("Descrição da demanda", first.id(), List.of(), requester);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var body = "{\"categoryId\":\"" + second.id() + "\",\"version\":" + ticket.version() + "}";
        var path = "/api/v1/tickets/" + ticket.id();
        mvc.perform(patch(path + "/classification").with(user(requester)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch(path + "/classification").with(user(agent)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value(second.name()));
        mvc.perform(patch(path + "/classification").with(user(agent)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/tickets").param("categoryId", second.id().toString()).with(user(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].categoryName").value(second.name()))
                .andExpect(jsonPath("$.content[0].subject").doesNotExist())
                .andExpect(jsonPath("$.content[0].priority").doesNotExist())
                .andExpect(jsonPath("$.content[0].dueAt").doesNotExist());
        mvc.perform(get("/api/v1/tickets").param("categoryId", first.id().toString()).with(user(requester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        for (var removed : List.of("priority", "deadline")) {
            mvc.perform(patch(path + "/" + removed).with(user(agent)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().is4xxClientError());
        }
        assertThat(notifications.list(agent.id(), Pageable.unpaged()).getContent())
                .anySatisfy(notification -> {
                    assertThat(notification.ticketId()).isEqualTo(ticket.id());
                    assertThat(notification.message()).isEqualTo(first.name());
                });
    }

    private AuthenticatedUser account(Role role) {
        var created = identity.create(UUID.randomUUID() + "@example.test", "API user", Set.of(role), null).user();
        identity.changePassword(created.id(), "Valid-test-password-123!");
        return new AuthenticatedUser(created.id(), created.email(), created.displayName(),
                "unused", created.roles(), true, false);
    }
}
