package eu.dtc4eu.website;

import eu.dtc4eu.website.security.dto.AuthDtos.TokenResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end CMS flow over the seeded placeholder content: auth, page builder
 * CRUD + publish gating, sanitization at the API boundary, contact inbox,
 * role gating for user management.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebsiteCmsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders adminHeaders() {
        TokenResponse tokens = rest.postForEntity("/api/admin/auth/login",
                Map.of("username", "webadmin", "password", "Dtc4eu!Web2026"), TokenResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    @Test
    @Order(1)
    void loginRefreshAndMe() {
        ResponseEntity<TokenResponse> login = rest.postForEntity("/api/admin/auth/login",
                Map.of("username", "webadmin", "password", "Dtc4eu!Web2026"), TokenResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<TokenResponse> refreshed = rest.postForEntity("/api/admin/auth/refresh",
                Map.of("refreshToken", login.getBody().refreshToken()), TokenResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(refreshed.getBody().accessToken());
        ResponseEntity<Map> me = rest.exchange("/api/admin/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(me.getBody()).containsEntry("username", "webadmin").containsEntry("role", "ADMIN");

        ResponseEntity<Map> badLogin = rest.postForEntity("/api/admin/auth/login",
                Map.of("username", "webadmin", "password", "wrong"), Map.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    void adminEndpointsRequireAuthentication() {
        assertThat(rest.getForEntity("/api/admin/pages", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(3)
    void seededContentIsServedPublicly() {
        ResponseEntity<List> nav = rest.getForEntity("/api/public/nav", List.class);
        assertThat(nav.getBody()).hasSize(5);
        assertThat((Map<String, Object>) nav.getBody().get(0)).containsEntry("slug", "home");

        ResponseEntity<Map> home = rest.getForEntity("/api/public/pages/home", Map.class);
        assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) home.getBody().get("blocks");
        assertThat(blocks).extracting(b -> b.get("blockType"))
                .containsExactly("HERO", "RICH_TEXT", "CARDS", "FACTS", "NEWS_LIST", "CTA");

        ResponseEntity<List> news = rest.getForEntity("/api/public/news?limit=10", List.class);
        assertThat(news.getBody()).hasSize(3);

        ResponseEntity<Map> gallery = rest.getForEntity("/api/public/galleries/project-gallery", Map.class);
        assertThat(gallery.getBody()).containsEntry("slug", "project-gallery");
    }

    @Test
    @Order(4)
    void pageBuilderCreateEditPublishFlow() {
        HttpHeaders admin = adminHeaders();

        // create → starts DRAFT and is invisible publicly
        ResponseEntity<Map> created = rest.exchange("/api/admin/pages", HttpMethod.POST,
                new HttpEntity<>(Map.of("slug", "Test Page!", "title", "Test Page", "showInNav", false,
                        "navOrder", 99), admin), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("slug", "test-page");
        Integer pageId = (Integer) created.getBody().get("id");

        assertThat(rest.getForEntity("/api/public/pages/test-page", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // atomic block save with reorderable list
        ResponseEntity<Map> withBlocks = rest.exchange("/api/admin/pages/" + pageId + "/blocks", HttpMethod.PUT,
                new HttpEntity<>(Map.of("blocks", List.of(
                        Map.of("blockType", "HERO", "payload", Map.of("heading", "Hello")),
                        Map.of("blockType", "RICH_TEXT", "payload", Map.of("bodyHtml", "<p>Body</p>")))), admin),
                Map.class);
        assertThat(withBlocks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) withBlocks.getBody().get("blocks")).hasSize(2);

        // invalid payloads are rejected with a helpful message
        ResponseEntity<Map> invalid = rest.exchange("/api/admin/pages/" + pageId + "/blocks", HttpMethod.PUT,
                new HttpEntity<>(Map.of("blocks", List.of(
                        Map.of("blockType", "HERO", "payload", Map.of("nonsense", "x")))), admin), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) invalid.getBody().get("message")).contains("unknown field");

        // script injection is stripped at the boundary
        rest.exchange("/api/admin/pages/" + pageId + "/blocks", HttpMethod.PUT,
                new HttpEntity<>(Map.of("blocks", List.of(
                        Map.of("blockType", "RICH_TEXT",
                                "payload", Map.of("bodyHtml", "<p>safe</p><script>alert(1)</script>")))), admin),
                Map.class);

        // publish → page becomes public with sanitized content
        rest.exchange("/api/admin/pages/" + pageId + "/publish", HttpMethod.POST, new HttpEntity<>(admin), Map.class);
        ResponseEntity<String> publicPage = rest.getForEntity("/api/public/pages/test-page", String.class);
        assertThat(publicPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicPage.getBody()).contains("safe").doesNotContain("<script");

        // duplicate slug conflicts
        ResponseEntity<Map> duplicate = rest.exchange("/api/admin/pages", HttpMethod.POST,
                new HttpEntity<>(Map.of("slug", "test-page", "title", "Other", "showInNav", false,
                        "navOrder", 100), admin), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // delete removes it publicly
        rest.exchange("/api/admin/pages/" + pageId, HttpMethod.DELETE, new HttpEntity<>(admin), Void.class);
        assertThat(rest.getForEntity("/api/public/pages/test-page", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    void newsPublishGating() {
        HttpHeaders admin = adminHeaders();
        ResponseEntity<Map> created = rest.exchange("/api/admin/news", HttpMethod.POST,
                new HttpEntity<>(Map.of("slug", "draft-post", "title", "Draft post",
                        "bodyHtml", "<p>text</p>"), admin), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(rest.getForEntity("/api/public/news/draft-post", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Integer newsId = (Integer) created.getBody().get("id");
        rest.exchange("/api/admin/news/" + newsId + "/publish", HttpMethod.POST, new HttpEntity<>(admin), Map.class);
        ResponseEntity<Map> publicPost = rest.getForEntity("/api/public/news/draft-post", Map.class);
        assertThat(publicPost.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicPost.getBody().get("publishedAt")).isNotNull();
    }

    @Test
    @Order(6)
    void contactFormFeedsInboxAndHoneypotIsSilent() {
        HttpHeaders admin = adminHeaders();

        ResponseEntity<Void> ok = rest.postForEntity("/api/public/contact",
                Map.of("name", "Visitor", "email", "visitor@example.eu", "message", "Hello project"), Void.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        // honeypot filled → accepted but never stored
        rest.postForEntity("/api/public/contact",
                Map.of("name", "Bot", "email", "bot@spam.io", "message", "spam", "website", "http://spam"),
                Void.class);

        // invalid email → 400
        ResponseEntity<Map> invalid = rest.postForEntity("/api/public/contact",
                Map.of("name", "X", "email", "not-an-email", "message", "y"), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<List> inbox = rest.exchange("/api/admin/contact-submissions", HttpMethod.GET,
                new HttpEntity<>(admin), List.class);
        assertThat(inbox.getBody()).hasSize(1);
        Map<String, Object> submission = (Map<String, Object>) inbox.getBody().get(0);
        assertThat(submission).containsEntry("name", "Visitor").containsEntry("read", false);

        rest.exchange("/api/admin/contact-submissions/" + submission.get("id") + "/read", HttpMethod.POST,
                new HttpEntity<>(Map.of("read", true), admin), Void.class);
        ResponseEntity<Map> unread = rest.exchange("/api/admin/contact-submissions/unread-count", HttpMethod.GET,
                new HttpEntity<>(admin), Map.class);
        assertThat(unread.getBody()).containsEntry("unread", 0);
    }

    @Test
    @Order(7)
    void userManagementIsAdminOnly() {
        HttpHeaders admin = adminHeaders();

        ResponseEntity<Map> editor = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "editor1", "password", "Editor!2026x",
                        "fullName", "Content Editor", "role", "EDITOR"), admin), Map.class);
        assertThat(editor.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // the editor can use content endpoints but not user management
        TokenResponse editorTokens = rest.postForEntity("/api/admin/auth/login",
                Map.of("username", "editor1", "password", "Editor!2026x"), TokenResponse.class).getBody();
        HttpHeaders editorHeaders = new HttpHeaders();
        editorHeaders.setBearerAuth(editorTokens.accessToken());

        assertThat(rest.exchange("/api/admin/pages", HttpMethod.GET, new HttpEntity<>(editorHeaders), List.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/admin/users", HttpMethod.GET, new HttpEntity<>(editorHeaders), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // the last active ADMIN cannot be demoted
        ResponseEntity<List> users = rest.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(admin), List.class);
        Map<String, Object> webadmin = (Map<String, Object>) users.getBody().stream()
                .filter(u -> "webadmin".equals(((Map<String, Object>) u).get("username"))).findFirst().orElseThrow();
        ResponseEntity<Map> demote = rest.exchange("/api/admin/users/" + webadmin.get("id"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("fullName", "Website Administrator", "role", "EDITOR", "active", true),
                        admin), Map.class);
        assertThat(demote.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(8)
    void galleryImageRulesAreEnforced() {
        HttpHeaders admin = adminHeaders();
        ResponseEntity<List> galleries = rest.exchange("/api/admin/galleries", HttpMethod.GET,
                new HttpEntity<>(admin), List.class);
        Integer galleryId = (Integer) ((Map<String, Object>) galleries.getBody().get(0)).get("id");

        // unknown media id is rejected
        ResponseEntity<Map> invalid = rest.exchange("/api/admin/galleries/" + galleryId + "/images", HttpMethod.PUT,
                new HttpEntity<>(Map.of("images", List.of(Map.of("mediaId", 424242))), admin), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
