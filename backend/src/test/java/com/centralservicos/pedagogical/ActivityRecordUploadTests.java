package com.centralservicos.pedagogical;

import com.centralservicos.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static com.centralservicos.pedagogical.ActivityRecordViews.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:record_upload;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class ActivityRecordUploadTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired IdentityService identity;
    @Autowired PedagogicalService classes;
    @Autowired ActivityRecordService records;

    @Test void realMultipartAcceptsFiftyPhotosAndEnforcesCombinedLimitOnEdit() throws Exception {
        var account = identity.create(UUID.randomUUID() + "@example.test", "Administrativo", Set.of(Role.MANAGER), null).user();
        identity.changePassword(account.id(), "permanent-password-123");
        var actor = new AuthenticatedUser(account.id(), account.email(), account.displayName(), "unused", account.roles(), true, false);
        var classroom = classes.saveClass(null, new PedagogicalViews.ClassInput("50 fotos", false, List.of(), null), actor);
        var base = "http://localhost:" + port + "/api/v1";
        try (var http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()) {
            var csrf = csrf(http, base);
            var login = HttpRequest.newBuilder(URI.create(base + "/auth/session"))
                .header("Content-Type", "application/json").header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("email", account.email(), "password", "permanent-password-123")))).build();
            assertThat(http.send(login, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            csrf = csrf(http, base);
            var created = upload(http, base + "/pedagogical/classes/" + classroom.id() + "/records", "POST", csrf,
                new Input("50 fotos", LocalDate.of(2026, 9, 29), null, List.of()), 50);
            assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
            var data = json.readTree(created.body());
            assertThat(data.get("photos").size()).isEqualTo(50);
            var id = UUID.fromString(data.get("id").asText());
            var saved = records.get(id, actor);
            var keep = saved.photos().stream().map(Photo::id).toList();
            var url = base + "/pedagogical/records/" + id;
            var updated = upload(http, url, "PUT", csrf, new Input("Manter 50", saved.activityDate(), saved.version(), keep), 0);
            assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
            var current = records.get(id, actor);
            assertThat(current.photos().stream().map(Photo::id)).containsExactlyElementsOf(keep);
            var overflow = upload(http, url, "PUT", csrf, new Input("Não salvar 51", current.activityDate(), current.version(), keep), 1);
            assertThat(overflow.statusCode()).as(overflow.body()).isEqualTo(422);
            assertThat(records.get(id, actor).title()).isEqualTo("Manter 50");
            var replace = upload(http, url, "PUT", csrf, new Input("Substituir uma", current.activityDate(), current.version(), keep.subList(1, 50)), 1);
            assertThat(replace.statusCode()).as(replace.body()).isEqualTo(200);
            var replaced = records.get(id, actor);
            assertThat(replaced.photos()).hasSize(50);
            assertThat(replaced.photos().subList(0, 49).stream().map(Photo::id)).containsExactlyElementsOf(keep.subList(1, 50));
            records.delete(id, replaced.version(), actor);
        }
    }

    private JsonNode csrf(HttpClient http, String base) throws Exception {
        return json.readTree(http.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    private HttpResponse<String> upload(HttpClient http, String url, String method, JsonNode csrf, Input metadata, int count) throws Exception {
        var boundary = "photos-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"metadata\"; filename=\"metadata.json\"\r\nContent-Type: application/json\r\n\r\n" + json.writeValueAsString(metadata) + "\r\n").getBytes(StandardCharsets.UTF_8));
        var image = ActivityRecordTests.png().getBytes();
        for (int i = 0; i < count; i++) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"foto" + i + ".png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(image); body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
