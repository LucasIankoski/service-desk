package com.centralservicos.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin-user-listing;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional
class AdminUserListingTests {
    @Autowired WebApplicationContext context;
    @Autowired IdentityService identity;
    @Autowired JdbcTemplate jdbc;

    @Test
    void listsBothPagesWithLegacyRolesWithoutChangingStoredPermissions() throws Exception {
        var created = identity.create("listing-admin@example.test", "Admin Listagem", Set.of(Role.ADMIN), null);
        identity.changePassword(created.user().id(), "permanent-password-123");
        var admin = identity.loadForAuthentication(created.user().email());
        for (int index = 1; index <= 21; index++) {
            insertUser("Usuário %02d".formatted(index), "REQUESTER");
        }
        var legacyOnly = insertUser("000 Legacy 1", "GUARDIAN");
        var mixed = insertUser("000 Legacy 2", "GUARDIAN");
        jdbc.update("insert into user_role(user_id,role_name) values(?,?)", mixed.toString(), "AGENT");

        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/admin/users?page=0&size=20").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(24))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].id").value(legacyOnly.toString()))
                .andExpect(jsonPath("$.content[0].roles").isEmpty())
                .andExpect(jsonPath("$.content[1].id").value(mixed.toString()))
                .andExpect(jsonPath("$.content[1].roles.length()").value(1))
                .andExpect(jsonPath("$.content[1].roles[0]").value("AGENT"))
                .andExpect(jsonPath("$.content[2].roles[0]").value("ADMIN"));
        mvc.perform(get("/api/v1/admin/users?page=1&size=20").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].displayName").value("Usuário 18"))
                .andExpect(jsonPath("$.content[3].displayName").value("Usuário 21"))
                .andExpect(jsonPath("$.content[3].roles[0]").value("REQUESTER"));
        mvc.perform(get("/api/v1/admin/users?page=2&size=20").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(24));
        assertThat(jdbc.queryForObject("select count(*) from user_role where role_name='GUARDIAN'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void listingRemainsRestrictedToAdministrators() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").with(user("requester").roles("REQUESTER")))
                .andExpect(status().isForbidden());
    }

    private UUID insertUser(String name, String role) {
        var id = UUID.randomUUID();
        jdbc.update("insert into user_account(id,email,display_name,password_hash,active,password_change_required,"
                        + "anonymized,created_at,updated_at) values(?,?,?,?,true,false,false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id.toString(), id + "@example.test", name, "unused-test-hash");
        jdbc.update("insert into user_role(user_id,role_name) values(?,?)", id.toString(), role);
        return id;
    }
}
