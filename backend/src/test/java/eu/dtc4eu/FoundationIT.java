package eu.dtc4eu;

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
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1 acceptance (spec §7): an admin can create a user, assign a role + scope,
 * create/select a cycle; non-admins cannot reach admin endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FoundationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders adminHeaders() {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Dtc4eu!Admin2026"), TokenResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    private HttpHeaders loginAs(String username, String password) {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), TokenResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    @Test
    @Order(1)
    void adminCreatesUserWithRoleAndScope() {
        Map<String, Object> payload = Map.of(
                "username", "facilitator1",
                "email", "f1@dtc4eu.eu",
                "fullName", "Internal Facilitator One",
                "password", "Facil1tator!",
                "roles", List.of("INTERNAL_FACILITATOR"),
                "facilityIds", List.of(),
                "ternaIds", List.of());

        ResponseEntity<Map> response = rest.exchange("/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(payload, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<String>) response.getBody().get("roles")).containsExactly("INTERNAL_FACILITATOR");
    }

    @Test
    @Order(2)
    void nonAdminCannotReachAdminEndpoints() {
        HttpHeaders facilitator = loginAs("facilitator1", "Facil1tator!");

        ResponseEntity<Map> list = rest.exchange("/api/v1/users", HttpMethod.GET,
                new HttpEntity<>(facilitator), Map.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> create = rest.exchange("/api/v1/cycles", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", "2027-2028"), facilitator), Map.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    void adminCreatesCycleAndSetsItCurrent() {
        ResponseEntity<Map> created = rest.exchange("/api/v1/cycles", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", "2027-2028", "startDate", "2027-09-01", "endDate", "2028-08-31"),
                        adminHeaders()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("current")).isEqualTo(false);

        Number cycleId = (Number) created.getBody().get("id");
        ResponseEntity<Map> setCurrent = rest.exchange("/api/v1/cycles/" + cycleId + "/set-current",
                HttpMethod.POST, new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(setCurrent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setCurrent.getBody().get("current")).isEqualTo(true);

        // exactly one current cycle: the seeded 2026-2027 is no longer current
        ResponseEntity<List> all = rest.exchange("/api/v1/cycles", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), List.class);
        long currentCount = ((List<Map<String, Object>>) all.getBody()).stream()
                .filter(c -> Boolean.TRUE.equals(c.get("current"))).count();
        assertThat(currentCount).isEqualTo(1);

        // switch back so later tests keep using the seeded cycle
        List<Map<String, Object>> cycles = all.getBody();
        Number seeded = (Number) cycles.stream().filter(c -> "2026-2027".equals(c.get("code"))).findFirst()
                .orElseThrow().get("id");
        rest.exchange("/api/v1/cycles/" + seeded + "/set-current", HttpMethod.POST,
                new HttpEntity<>(adminHeaders()), Map.class);
    }

    @Test
    @Order(4)
    void unknownCycleHeaderIsRejected() {
        HttpHeaders headers = adminHeaders();
        headers.set("X-Cycle-Id", "99999");

        ResponseEntity<Map> response = rest.exchange("/api/v1/users", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("message")).contains("Unknown cycle");
    }

    @Test
    @Order(5)
    void selfDeactivationIsRejected() {
        ResponseEntity<Map> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), Map.class);
        Number adminId = (Number) ((Map<String, Object>) me.getBody().get("user")).get("id");

        Map<String, Object> payload = Map.of(
                "fullName", "Super Administrator",
                "active", false,
                "roles", List.of("SUPER_ADMIN"));
        ResponseEntity<Map> response = rest.exchange("/api/v1/users/" + adminId, HttpMethod.PUT,
                new HttpEntity<>(payload, adminHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
