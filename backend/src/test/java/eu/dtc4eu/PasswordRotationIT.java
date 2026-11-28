package eu.dtc4eu;

import eu.dtc4eu.security.PasswordPolicyService;
import eu.dtc4eu.security.dto.AuthDtos.TokenResponse;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FIX-LIST X4 (temporary passwords, forced rotation of the published seed admin
 * password) and Q1 (optional admin-chosen default cycle).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class PasswordRotationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    private static final String SEED = "Dtc4eu!Admin2026";
    private static String adminPassword = SEED;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PasswordPolicyService passwordPolicyService;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private TokenResponse login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login", Map.of("username", username, "password", password),
                TokenResponse.class).getBody();
    }

    private HttpHeaders bearer(TokenResponse tokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    private ResponseEntity<Map> changePassword(TokenResponse tokens, String current, String next) {
        return rest.exchange("/api/v1/auth/me/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", current, "newPassword", next), bearer(tokens)), Map.class);
    }

    @Test
    @Order(1)
    void aTemporaryPasswordAllowsNothingButChangingIt() {
        HttpHeaders admin = bearer(login("admin", adminPassword));
        ResponseEntity<Map> created = rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "temp-user", "fullName", "Temp User", "password", "Temporary1!",
                "roles", List.of("RESEARCH_TEAM"), "mustChangePassword", true), admin), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TokenResponse tokens = login("temp-user", "Temporary1!");
        assertThat(tokens.user().mustChangePassword()).isTrue();
        ResponseEntity<Map> blocked = rest.exchange("/api/v1/facilities", HttpMethod.GET,
                new HttpEntity<>(bearer(tokens)), Map.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody().get("message")).isEqualTo("Password change required");
        assertThat(rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(tokens)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(changePassword(tokens, "wrong-password", "Brand-new-pass1").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(tokens, "Temporary1!", SEED).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(tokens, "Temporary1!", "short").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(tokens, "Temporary1!", "Brand-new-pass1").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.exchange("/api/v1/facilities", HttpMethod.GET, new HttpEntity<>(bearer(tokens)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse again = login("temp-user", "Brand-new-pass1");
        assertThat(again.user().mustChangePassword()).isFalse();
    }

    @Test
    @Order(2)
    void theSeedAdminIsForcedToRotateThePublishedPassword() {
        assertThat(passwordPolicyService.flagSeedAdminIfUnrotated()).isTrue();
        TokenResponse tokens = login("admin", SEED);
        assertThat(tokens.user().mustChangePassword()).isTrue();
        assertThat(rest.exchange("/api/v1/users", HttpMethod.GET, new HttpEntity<>(bearer(tokens)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(changePassword(tokens, SEED, "Rotated-pass-2026").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        adminPassword = "Rotated-pass-2026";
        // rotated: the start-up check leaves it alone from now on
        assertThat(passwordPolicyService.flagSeedAdminIfUnrotated()).isFalse();
        assertThat(rest.exchange("/api/v1/users", HttpMethod.GET,
                new HttpEntity<>(bearer(login("admin", adminPassword))), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(3)
    void atMostOneCycleIsTheLandingDefault() {
        HttpHeaders admin = bearer(login("admin", adminPassword));
        Number older = (Number) rest.exchange("/api/v1/cycles", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", "2025-2026"), admin), Map.class).getBody().get("id");
        List<Map<String, Object>> cycles = rest.exchange("/api/v1/cycles", HttpMethod.GET, new HttpEntity<>(admin),
                List.class).getBody();
        assertThat(cycles).allMatch(c -> Boolean.FALSE.equals(c.get("isDefault")));
        Number newer = (Number) cycles.stream().filter(c -> "2026-2027".equals(c.get("code"))).findFirst()
                .orElseThrow().get("id");

        for (Number id : List.of(newer, older)) {
            ResponseEntity<Map> marked = rest.exchange("/api/v1/cycles/" + id, HttpMethod.PUT,
                    new HttpEntity<>(Map.of("isDefault", true), admin), Map.class);
            assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        List<Map<String, Object>> after = rest.exchange("/api/v1/cycles", HttpMethod.GET, new HttpEntity<>(admin),
                List.class).getBody();
        assertThat(after).filteredOn(c -> Boolean.TRUE.equals(c.get("isDefault")))
                .extracting(c -> ((Number) c.get("id")).longValue()).containsExactly(older.longValue());

        rest.exchange("/api/v1/cycles/" + older, HttpMethod.PUT,
                new HttpEntity<>(Map.of("isDefault", false, "status", "CLOSED"), admin), Map.class);
    }

    /** The published seed password never works in production: it is swapped for the private bootstrap one. */
    @Test
    @Order(4)
    void theSeedPasswordIsReplacedByTheBootstrapPasswordAtStartUp() {
        jdbc.update("update users set password_hash = :hash, must_change_password = false where username = 'admin'",
                new MapSqlParameterSource("hash", passwordEncoder.encode(SEED)));

        // no bootstrap password configured: start-up refuses rather than keep the published one
        assertThatThrownBy(() -> passwordPolicyService.replaceSeedAdminPasswordIfUnrotated(""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SEED_ADMIN_PASSWORD");
        assertThatThrownBy(() -> passwordPolicyService.replaceSeedAdminPasswordIfUnrotated(SEED))
                .isInstanceOf(IllegalStateException.class);

        assertThat(passwordPolicyService.replaceSeedAdminPasswordIfUnrotated("Bootstrap-pass-2026")).isTrue();
        assertThat(rest.postForEntity("/api/v1/auth/login", Map.of("username", "admin", "password", SEED),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        TokenResponse tokens = login("admin", "Bootstrap-pass-2026");
        assertThat(tokens.user().mustChangePassword()).isTrue();

        assertThat(passwordPolicyService.replaceSeedAdminPasswordIfUnrotated("")).isFalse();
    }

    /** A password change refuses every refresh token issued before it, so other sessions end with their access token. */
    @Test
    @Order(5)
    void aPasswordChangeEndsEveryOtherSession() throws InterruptedException {
        TokenResponse other = login("temp-user", "Brand-new-pass1");
        TokenResponse self = login("temp-user", "Brand-new-pass1");
        Thread.sleep(1100); // JWT iat has whole-second precision

        assertThat(changePassword(self, "Brand-new-pass1", "Third-pass-2026").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        for (TokenResponse stale : List.of(other, self)) {
            assertThat(refresh(stale).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(refresh(login("temp-user", "Third-pass-2026")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> refresh(TokenResponse tokens) {
        return rest.postForEntity("/api/v1/auth/refresh", Map.of("refreshToken", tokens.refreshToken()), Map.class);
    }
}
