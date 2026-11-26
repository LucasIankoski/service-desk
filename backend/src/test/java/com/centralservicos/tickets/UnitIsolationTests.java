package com.centralservicos.tickets;

import com.centralservicos.TestUnits;
import com.centralservicos.identity.AuthenticatedUser;
import com.centralservicos.identity.IdentityService;
import com.centralservicos.identity.Role;
import com.centralservicos.identity.UnitService;
import com.centralservicos.notifications.NotificationService;
import com.centralservicos.shared.TenantContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada unidade enxerga apenas os próprios dados; ADMIN entra em qualquer unidade ativa. */
@SpringBootTest
@ActiveProfiles("test")
class UnitIsolationTests {

    private static final String UNIT_HEADER = "X-Unit-Id";

    @Autowired WebApplicationContext context;
    @Autowired IdentityService identity;
    @Autowired UnitService units;
    @Autowired CategoryService categories;
    @Autowired NotificationService notifications;
    @Autowired TicketRepository tickets;
    @Autowired TicketService ticketService;

    MockMvc mvc;
    UUID otherUnit;
    String otherCode;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        otherCode = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
        otherUnit = units.create(otherCode, "Unidade " + otherCode, null, null, null, null).id();
    }

    @Test
    void ticketsOfAnotherUnitAreInvisibleAndNumberedByUnit() throws Exception {
        var localAgent = account(false, Map.of(TestUnits.DEFAULT_UNIT_ID, Set.of(Role.AGENT, Role.REQUESTER)));
        var otherRequester = account(false, Map.of(otherUnit, Set.of(Role.REQUESTER)));
        var otherAgent = account(false, Map.of(otherUnit, Set.of(Role.AGENT)));
        UUID category;
        try (var ignored = TenantContext.enter(otherUnit)) {
            category = categories.create("Manutenção", otherAgent.id()).id();
        }
        categories.createIfMissing("Manutenção", localAgent.id());

        var created = mvc.perform(multipart("/api/v1/tickets").file(metadata(category))
                        .header(UNIT_HEADER, otherUnit.toString()).with(user(otherRequester)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicNumber").value(startsWith(otherCode + "-")))
                .andReturn();
        String ticketId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/tickets/" + ticketId).with(user(localAgent)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tickets/" + ticketId).header(UNIT_HEADER, otherUnit.toString())
                        .with(user(localAgent)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tickets").param("size", "200").with(user(localAgent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(ticketId))));
        mvc.perform(get("/api/v1/tickets/" + ticketId).header(UNIT_HEADER, otherUnit.toString())
                        .with(user(otherAgent)))
                .andExpect(status().isOk());

        assertThat(notifications.list(otherAgent.id(), Set.of(otherUnit), Pageable.unpaged()).getContent())
                .anySatisfy(notice -> assertThat(notice.ticketId()).hasToString(ticketId));
        assertThat(notifications.list(localAgent.id(), Set.of(TestUnits.DEFAULT_UNIT_ID, otherUnit),
                Pageable.unpaged()).getContent())
                .noneSatisfy(notice -> assertThat(notice.ticketId()).hasToString(ticketId));
    }

    @Test
    void administratorSwitchesUnitsAndMustChooseOneWhenSeveralExist() throws Exception {
        var admin = account(true, Map.of());
        String otherCategory;
        try (var ignored = TenantContext.enter(otherUnit)) {
            otherCategory = categories.create("Exclusiva " + otherCode, admin.id()).name();
        }

        mvc.perform(get("/api/v1/admin/categories").header(UNIT_HEADER, otherUnit.toString()).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(otherCategory)));
        mvc.perform(get("/api/v1/admin/categories").header(UNIT_HEADER, TestUnits.DEFAULT_UNIT_ID.toString())
                        .with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem(otherCategory))));
        mvc.perform(get("/api/v1/admin/categories").with(user(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/me").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.units[*].id", hasItem(otherUnit.toString())))
                .andExpect(jsonPath("$.units[*].id", hasItem(TestUnits.DEFAULT_UNIT_ID.toString())));
    }

    @Test
    void memberOfOneUnitGetsItAutomaticallyAndCannotOpenOthers() throws Exception {
        var member = account(false, Map.of(otherUnit, Set.of(Role.MANAGER)));

        mvc.perform(get("/api/v1/auth/me").with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUnitId").value(otherUnit.toString()))
                .andExpect(jsonPath("$.roles", hasItem("MANAGER")))
                .andExpect(jsonPath("$.units.length()").value(1));
        mvc.perform(get("/api/v1/tickets").header(UNIT_HEADER, TestUnits.DEFAULT_UNIT_ID.toString())
                        .with(user(member)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tickets").header(UNIT_HEADER, "not-a-unit").with(user(member)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/units").with(user(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemScopeSeesEveryUnitWhileUnitScopeIsFiltered() {
        var requester = account(false, Map.of(otherUnit, Set.of(Role.REQUESTER)));
        try (var ignored = TenantContext.enter(otherUnit)) {
            var category = categories.create("Sistema " + otherCode, requester.id()).id();
            ticketService.create("Chamado da outra unidade", category, java.util.List.of(), requester);
        }
        long local = tickets.count();
        long all;
        try (var ignored = TenantContext.asSystem()) {
            all = tickets.count();
        }
        long other;
        try (var ignored = TenantContext.enter(otherUnit)) {
            other = tickets.count();
        }
        assertThat(other).isEqualTo(1);
        assertThat(all).isGreaterThanOrEqualTo(local + other);
    }

    private MockMultipartFile metadata(UUID category) {
        return new MockMultipartFile("metadata", "", "application/json",
                ("{\"description\":\"Pedido isolado\",\"categoryId\":\"" + category + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private AuthenticatedUser account(boolean admin, Map<UUID, Set<Role>> unitRoles) {
        var created = identity.create(UUID.randomUUID() + "@example.test", "Unidade " + UUID.randomUUID(),
                admin, unitRoles, null).user();
        identity.changePassword(created.id(), "Valid-test-password-123!");
        return identity.loadForAuthentication(created.email());
    }
}
