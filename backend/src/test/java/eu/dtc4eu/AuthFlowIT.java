package eu.dtc4eu;

import eu.dtc4eu.security.dto.AuthDtos.TokenResponse;
import org.junit.jupiter.api.Test;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T0 acceptance (spec §7): the app boots, V1 migrates cleanly against
 * Postgres 16, and POST /auth/login issues a token for the seeded super-admin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginIssuesTokensForSeededSuperAdmin() {
        ResponseEntity<TokenResponse> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Dtc4eu!Admin2026"), TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
        assertThat(body.user().username()).isEqualTo("admin");
        assertThat(body.user().roles()).containsExactly("SUPER_ADMIN");
    }

    @Test
    void wrongPasswordIsRejectedWithSpecErrorShape() {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "wrong"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .containsEntry("status", 401)
                .containsEntry("path", "/api/v1/auth/login")
                .containsKeys("timestamp", "error", "message");
    }

    @Test
    void meReturnsUserScopeAndSeededCurrentCycle() {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Dtc4eu!Admin2026"), TokenResponse.class).getBody();
        assertThat(tokens).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        ResponseEntity<Map> response = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> currentCycle = (Map<?, ?>) response.getBody().get("currentCycle");
        assertThat(currentCycle).isNotNull();
        assertThat(currentCycle.get("code")).isEqualTo("2026-2027");
        assertThat(currentCycle.get("status")).isEqualTo("OPEN");
    }

    @Test
    void refreshExchangesRefreshTokenForNewPair() {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Dtc4eu!Admin2026"), TokenResponse.class).getBody();
        assertThat(tokens).isNotNull();

        ResponseEntity<TokenResponse> response = rest.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", tokens.refreshToken()), TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void accessTokenIsRejectedByRefreshEndpoint() {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Dtc4eu!Admin2026"), TokenResponse.class).getBody();
        assertThat(tokens).isNotNull();

        ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", tokens.accessToken()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousRequestGets401InSpecShape() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/auth/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", 401).containsKeys("timestamp", "error", "path");
    }
}
