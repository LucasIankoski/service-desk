package com.centralservicos.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SecurityIntegrationTests {

    @Autowired WebApplicationContext context;
    @Autowired IdentityService identity;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void csrfIsIssuedAndRequiredForLogin() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"));

        mvc.perform(post("/api/v1/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.test\",\"password\":\"invalid\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void temporaryPasswordCanOnlyBeChangedBeforeUsingTheApi() throws Exception {
        var created = identity.create(uniqueEmail("temporary"), "Temporary User", Set.of(Role.REQUESTER), null);
        var session = login(created.user().email(), created.temporaryPassword());

        mvc.perform(get("/api/v1/tickets").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Troque a senha temporária")));

        mvc.perform(post("/api/v1/auth/password/change")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"permanent-password-123\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/tickets?page=0&size=1").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void rolesAndAccountStateAreRefreshedDuringAnExistingSession() throws Exception {
        var fallback = identity.create(uniqueEmail("fallback-admin"), "Fallback Admin", Set.of(Role.ADMIN), null);
        var created = identity.create(uniqueEmail("admin"), "Session Admin", Set.of(Role.ADMIN), null);
        identity.changePassword(created.user().id(), "permanent-password-123");
        var session = login(created.user().email(), "permanent-password-123");

        mvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isOk());

        identity.update(created.user().id(), "Session Admin", Set.of(Role.REQUESTER), true, fallback.user().id());
        mvc.perform(get("/api/v1/admin/users").session(session))
                .andExpect(status().isForbidden());

        identity.update(created.user().id(), "Session Admin", Set.of(Role.REQUESTER), false, null);
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requesterCannotReachAdministrativeEndpoints() throws Exception {
        var created = identity.create(uniqueEmail("requester"), "Requester", Set.of(Role.REQUESTER), null);
        identity.changePassword(created.user().id(), "permanent-password-123");
        var session = login(created.user().email(), "permanent-password-123");

        mvc.perform(get("/api/v1/admin/settings").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/assignees").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void agendaEndpointsEnforceRoleSpecificAccess() throws Exception {
        var requester = identity.create(uniqueEmail("agenda-requester"), "Agenda Requester",
                Set.of(Role.REQUESTER), null);
        identity.changePassword(requester.user().id(), "permanent-password-123");
        var requesterSession = login(requester.user().email(), "permanent-password-123");

        mvc.perform(get("/api/v1/agenda/items")
                        .param("start", "2026-09-01T00:00:00Z")
                        .param("end", "2026-10-01T00:00:00Z")
                        .session(requesterSession))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/agenda/items")
                        .session(requesterSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/managers").session(requesterSession))
                .andExpect(status().isForbidden());

        var agent = identity.create(uniqueEmail("agenda-agent"), "Agenda Agent", Set.of(Role.AGENT), null);
        identity.changePassword(agent.user().id(), "permanent-password-123");
        var agentSession = login(agent.user().email(), "permanent-password-123");
        mvc.perform(get("/api/v1/agenda/items")
                        .param("start", "2026-09-01T00:00:00Z")
                        .param("end", "2026-10-01T00:00:00Z")
                        .session(agentSession))
                .andExpect(status().isForbidden());

        var manager = identity.create(uniqueEmail("agenda-manager"), "Agenda Manager", Set.of(Role.MANAGER), null);
        identity.changePassword(manager.user().id(), "permanent-password-123");
        var managerSession = login(manager.user().email(), "permanent-password-123");
        mvc.perform(get("/api/v1/users/managers").session(managerSession))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/agenda/items/00000000-0000-0000-0000-000000000000/status")
                        .session(managerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\",\"version\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void absoluteSessionLifetimeIsEnforced() throws Exception {
        var created = identity.create(uniqueEmail("expired-session"), "Expired Session",
                Set.of(Role.REQUESTER), null);
        identity.changePassword(created.user().id(), "permanent-password-123");
        var session = login(created.user().email(), "permanent-password-123");
        session.setAttribute(AbsoluteSessionTimeoutFilter.AUTHENTICATED_AT,
                System.currentTimeMillis() - Duration.ofHours(13).toMillis());

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedLoginFailuresAreRateLimited() throws Exception {
        var email = uniqueEmail("rate-limit");
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/auth/session")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"invalid-password\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"invalid-password\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/session")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }
}
