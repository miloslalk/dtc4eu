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
 * Phase 2 acceptance: Module 3 audits (coordinator auto-scope, evaluator
 * count rule, accredited outcomes), the structured GAS-Set (validation +
 * Section N restriction) and the Modules 6–8 additions (assessor stamp,
 * manual Excellence Points).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Phase2IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long facilityId;
    private static Long auditId;
    private static Long auditCoordinatorId;
    private static Long cycleId;

    private HttpHeaders headersOf(String username, String password) {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), TokenResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    private HttpHeaders admin() {
        return headersOf("admin", "Dtc4eu!Admin2026");
    }

    @Test
    @Order(1)
    void setUpFacilityAndAuditCoordinator() {
        ResponseEntity<Map> facility = rest.exchange("/api/v1/facilities", HttpMethod.POST,
                new HttpEntity<>(Map.of("facilityCode", "F-P2-01", "facilityType", "TC",
                        "name", "Phase 2 Community", "country", "Italy"), admin()), Map.class);
        assertThat(facility.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        facilityId = ((Number) facility.getBody().get("id")).longValue();

        ResponseEntity<Map> coordinator = rest.exchange("/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "auditor1", "password", "Auditor!2026x",
                        "fullName", "Audrey Auditor", "roles", List.of("AUDIT_COORDINATOR")), admin()), Map.class);
        assertThat(coordinator.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        auditCoordinatorId = ((Number) coordinator.getBody().get("id")).longValue();

        ResponseEntity<Map> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        cycleId = ((Number) ((Map<String, Object>) me.getBody().get("defaultCycle")).get("id")).longValue();

        // before any audit assignment the coordinator sees nothing
        ResponseEntity<Map> beforeScope = rest.exchange("/api/v1/facilities/" + facilityId, HttpMethod.GET,
                new HttpEntity<>(headersOf("auditor1", "Auditor!2026x")), Map.class);
        assertThat(beforeScope.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(2)
    void auditCreationAutoGrantsCoordinatorScope() {
        ResponseEntity<Map> created = rest.exchange("/api/v1/audits", HttpMethod.POST,
                new HttpEntity<>(Map.of("auditCode", "A1", "facilityId", facilityId, "auditType", "SIMPLE",
                        "renewalNumber", 1, "coordinatorIds", List.of(auditCoordinatorId),
                        "evaluatorIds", List.of()), admin()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        auditId = ((Number) created.getBody().get("id")).longValue();
        assertThat((List<?>) created.getBody().get("coordinators")).hasSize(1);

        // the coordinator now sees the audited facility AND the audit
        HttpHeaders coordinator = headersOf("auditor1", "Auditor!2026x");
        assertThat(rest.exchange("/api/v1/facilities/" + facilityId, HttpMethod.GET,
                new HttpEntity<>(coordinator), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<List> audits = rest.exchange("/api/v1/audits", HttpMethod.GET,
                new HttpEntity<>(coordinator), List.class);
        assertThat(audits.getBody()).hasSize(1);

        // duplicate code in the same cycle → 409
        ResponseEntity<Map> duplicate = rest.exchange("/api/v1/audits", HttpMethod.POST,
                new HttpEntity<>(Map.of("auditCode", "A1", "facilityId", facilityId, "auditType", "BQ",
                        "coordinatorIds", List.of(), "evaluatorIds", List.of()), admin()), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(3)
    void evaluatorCountRuleFollowsAuditType() {
        ResponseEntity<Map> e1 = rest.exchange("/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "eval1", "password", "Evaluator!26x",
                        "fullName", "Eva One", "roles", List.of("EVALUATOR")), admin()), Map.class);
        ResponseEntity<Map> e2 = rest.exchange("/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "eval2", "password", "Evaluator!26x",
                        "fullName", "Eva Two", "roles", List.of("EVALUATOR")), admin()), Map.class);
        Long evaluator1 = ((Number) e1.getBody().get("id")).longValue();
        Long evaluator2 = ((Number) e2.getBody().get("id")).longValue();

        // SIMPLE allows one evaluator only
        ResponseEntity<Map> tooMany = rest.exchange("/api/v1/audits/" + auditId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("auditCode", "A1", "facilityId", facilityId, "auditType", "SIMPLE",
                        "coordinatorIds", List.of(auditCoordinatorId),
                        "evaluatorIds", List.of(evaluator1, evaluator2)), admin()), Map.class);
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // CGQ allows up to three
        ResponseEntity<Map> ok = rest.exchange("/api/v1/audits/" + auditId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("auditCode", "A1", "facilityId", facilityId, "auditType", "CGQ",
                        "coordinatorIds", List.of(auditCoordinatorId),
                        "evaluatorIds", List.of(evaluator1, evaluator2)), admin()), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) ok.getBody().get("evaluators")).hasSize(2);
    }

    @Test
    @Order(4)
    void coordinatorSavesAccreditedOutcomes() {
        HttpHeaders coordinator = headersOf("auditor1", "Auditor!2026x");
        ResponseEntity<Map> saved = rest.exchange("/api/v1/audits/" + auditId + "/outcomes", HttpMethod.PUT,
                new HttpEntity<>(Map.of("processCodes", List.of("AQP-1", "AQP-2"),
                        "areaIds", List.of(), "items", List.of()), coordinator), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<String>) saved.getBody().get("accreditedProcesses"))
                .containsExactly("AQP-1", "AQP-2");

        // unknown area id → 400
        ResponseEntity<Map> invalid = rest.exchange("/api/v1/audits/" + auditId + "/outcomes", HttpMethod.PUT,
                new HttpEntity<>(Map.of("processCodes", List.of(), "areaIds", List.of(424242),
                        "items", List.of()), admin()), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(5)
    void gasSetIsValidatedAndSectionNRestricted() {
        // schema is served; admin sees Section N
        ResponseEntity<Map> schema = rest.exchange("/api/v1/facilities/" + facilityId + "/set-analysis-grid/schema",
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        assertThat(schema.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> sections = (List<Map<String, Object>>) schema.getBody().get("sections");
        assertThat(sections).extracting(s -> s.get("key")).contains("a", "b", "g", "n");

        // valid partial payload saves (lenient: no required-field gate)
        ResponseEntity<Map> saved = rest.exchange("/api/v1/facilities/" + facilityId + "/set-analysis-grid",
                HttpMethod.PUT, new HttpEntity<>(Map.of("gridVariant", "GAS", "payload", Map.of(
                        "a2_location", "City",
                        "b_collaborations", List.of("B.4 ART THERAPY SERVICES"),
                        "c1_ptp_r_planned", true,
                        "n_users", List.of(Map.of("user_code", "U-01", "sex", "M", "age", 34)))),
                        admin()), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);

        // unknown key → 400
        ResponseEntity<Map> unknown = rest.exchange("/api/v1/facilities/" + facilityId + "/set-analysis-grid",
                HttpMethod.PUT, new HttpEntity<>(Map.of("gridVariant", "GAS",
                        "payload", Map.of("not_a_field", 1)), admin()), Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // multiselect option outside the catalogue → 400
        ResponseEntity<Map> badOption = rest.exchange("/api/v1/facilities/" + facilityId + "/set-analysis-grid",
                HttpMethod.PUT, new HttpEntity<>(Map.of("gridVariant", "GAS",
                        "payload", Map.of("b_collaborations", List.of("B.99 NOT A THING"))), admin()), Map.class);
        assertThat(badOption.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // legacy free-form variants still work
        ResponseEntity<Map> legacy = rest.exchange("/api/v1/facilities/" + facilityId + "/set-analysis-grid",
                HttpMethod.PUT, new HttpEntity<>(Map.of("gridVariant", "A",
                        "payload", Map.of("anything", "goes")), admin()), Map.class);
        assertThat(legacy.getStatusCode()).isEqualTo(HttpStatus.OK);

        // the audit coordinator (non-clinical) gets neither Section N schema nor its data
        HttpHeaders coordinator = headersOf("auditor1", "Auditor!2026x");
        ResponseEntity<Map> restrictedSchema = rest.exchange(
                "/api/v1/facilities/" + facilityId + "/set-analysis-grid/schema", HttpMethod.GET,
                new HttpEntity<>(coordinator), Map.class);
        List<Map<String, Object>> visible = (List<Map<String, Object>>) restrictedSchema.getBody().get("sections");
        assertThat(visible).extracting(s -> s.get("key")).contains("a").doesNotContain("n");

        ResponseEntity<List> grids = rest.exchange("/api/v1/facilities/" + facilityId + "/set-analysis-grid",
                HttpMethod.GET, new HttpEntity<>(coordinator), List.class);
        List<Map<String, Object>> gridRows = (List<Map<String, Object>>) grids.getBody();
        Map<String, Object> gas = gridRows.stream()
                .filter(row -> "GAS".equals(row.get("gridVariant")))
                .findFirst().orElseThrow();
        assertThat((Map<String, Object>) gas.get("payload")).containsKey("a2_location").doesNotContainKey("n_users");
    }

    @Test
    @Order(6)
    void documentAssessorIsStampedAndManualEpsFeedHistory() {
        ResponseEntity<Map> document = rest.exchange("/api/v1/facilities/" + facilityId + "/document-applications",
                HttpMethod.POST, new HttpEntity<>(Map.of("standardType", "GPP", "standardCode", "GPP CT4-LC",
                        "serviceName", "Alpha", "applicationLevelPct", 40), admin()), Map.class);
        assertThat(document.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(document.getBody().get("assessedByName")).isEqualTo("Super Administrator");
        assertThat(document.getBody().get("assessedAt")).isNotNull();

        // manual ViVaCom EP for the cycle
        ResponseEntity<List> manual = rest.exchange(
                "/api/v1/facilities/" + facilityId + "/manual-excellence-points", HttpMethod.PUT,
                new HttpEntity<>(Map.of("cycleId", cycleId, "questionnaireCode", "vivacom",
                        "labels", List.of("1.2", "4.16", "1.2")), admin()), List.class);
        assertThat(manual.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manual.getBody()).hasSize(2);   // duplicates collapsed

        ResponseEntity<Map> history = rest.exchange("/api/v1/facilities/" + facilityId + "/history",
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        List<Map<String, Object>> cycles = (List<Map<String, Object>>) history.getBody().get("cycles");
        Map<String, List<String>> eps = (Map<String, List<String>>) cycles.get(0)
                .get("excellencePointsByQuestionnaire");
        assertThat(eps.get("VIVACOM")).containsExactly("1.2", "4.16");
    }
}
