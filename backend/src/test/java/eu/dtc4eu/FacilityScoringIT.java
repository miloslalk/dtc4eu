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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.2 register §3 acceptance: submitted administrations of the six DTCRO
 * forms feed the facility-cycle scoring engine (Quant-SPWP / Quant-SAWA with
 * Excel AVERAGE-skip-missing semantics and TAB.5 classifications), the point
 * metrics, the Summary Table, the terna benchmark areas and the TAB.2
 * population table. Recompute is idempotent; out-of-scope reads are 404.
 *
 * Fixture values: 2× USER (all 4s / all 2s), 1× STAFF (all 3s),
 * 1× LONG_FORM (all 4s), 1× DELEGATION (all 1s) — hand-computed expectations:
 *   coordinate with Del (letters a–c):    Final = AVG(3, 4, 1) = 2.667 → INTERMEDIATE
 *   coordinate without Del (letters d–e): Final = AVG(3, 4)    = 3.500 → EFFICACY
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FacilityScoringIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long dtcroId;
    private static Long facility1;
    private static Long facility2;
    private static Long facility3;
    private static Long ternaId;
    private static Map<String, Long> formIds = new HashMap<>();
    private static Map<Long, List<Long>> itemsByForm = new HashMap<>();

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

    private Long createFacility(String code) {
        ResponseEntity<Map> created = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", code, "facilityType", "GA", "facilitySubtype", "SIMPLE_GA",
                "name", "Scoring " + code), admin()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) created.getBody().get("id")).longValue();
    }

    /** Create → answer every form item with {@code value} → submit. */
    private void submitAdministration(String formCode, String subjectCode, int value,
                                      Map<String, Object> profile) {
        Long formId = formIds.get(formCode);
        Map<String, Object> payload = new HashMap<>(Map.of(
                "questionnaireId", dtcroId, "formId", formId, "facilityId", facility1,
                "subjectCode", subjectCode));
        if (profile != null) {
            payload.put("profileSnapshot", profile);
        }
        ResponseEntity<Map> created = rest.exchange("/api/v1/responses", HttpMethod.POST,
                new HttpEntity<>(payload, admin()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long responseId = ((Number) created.getBody().get("id")).longValue();

        List<Map<String, Object>> answers = new ArrayList<>();
        for (Long itemId : itemsByForm.get(formId)) {
            answers.add(Map.of("itemId", itemId, "numericValue", value));
        }
        ResponseEntity<Map> saved = rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", answers), admin()), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> submitted = rest.exchange("/api/v1/responses/" + responseId + "/submit",
                HttpMethod.POST, new HttpEntity<>(admin()), List.class);
        assertThat(submitted.getStatusCode()).as("submit %s", formCode).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> itemRow(List<Map<String, Object>> items, String coordinate) {
        return items.stream().filter(i -> coordinate.equals(i.get("coordinate"))).findFirst().orElseThrow();
    }

    private double num(Object value) {
        return Double.parseDouble(String.valueOf(value));
    }

    @Test
    @Order(1)
    void setUpTernaRosterAndDefinitions() {
        facility1 = createFacility("F-SC-1");
        facility2 = createFacility("F-SC-2");
        facility3 = createFacility("F-SC-3");

        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "sccoordinator", "fullName", "SC Coordinator", "password", "ScCoordPass!",
                "roles", List.of("COORDINATOR")), admin()), Map.class);
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "scfacilitator2", "fullName", "SC Facilitator Two", "password", "ScFacilPass!",
                "roles", List.of("INTERNAL_FACILITATOR"), "facilityIds", List.of(facility2)), admin()), Map.class);

        ResponseEntity<Map> page = rest.exchange("/api/v1/users?role=COORDINATOR&size=50", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        Number coordinatorId = (Number) ((List<Map<String, Object>>) page.getBody().get("content")).stream()
                .filter(u -> "sccoordinator".equals(u.get("username"))).findFirst().orElseThrow().get("id");

        ResponseEntity<Map> terna = rest.exchange("/api/v1/ternas", HttpMethod.POST, new HttpEntity<>(Map.of(
                "ternaCode", "TSC-1", "ternaType", "GAS",
                "facilityIds", List.of(facility1, facility2, facility3),
                "coordinatorIds", List.of(coordinatorId),
                "visitDates", List.of()), admin()), Map.class);
        assertThat(terna.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ternaId = ((Number) terna.getBody().get("id")).longValue();

        // pseudonymous member roster of facility1 → the TAB.2 denominator
        for (String[] member : new String[][]{{"U-001", "USER"}, {"U-002", "USER"}, {"S-001", "STAFF"}}) {
            rest.exchange("/api/v1/facilities/" + facility1 + "/members", HttpMethod.POST,
                    new HttpEntity<>(Map.of("memberCode", member[0], "category", member[1]), admin()), Map.class);
        }

        // DTCRO definition: form ids and per-form item ids
        ResponseEntity<List> questionnaires = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class);
        dtcroId = ((Number) ((List<Map<String, Object>>) questionnaires.getBody()).stream()
                .filter(q -> "DTCRO".equals(q.get("code"))).findFirst().orElseThrow().get("id")).longValue();
        ResponseEntity<Map> detail = rest.exchange("/api/v1/questionnaires/" + dtcroId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        for (Map<String, Object> form : (List<Map<String, Object>>) detail.getBody().get("forms")) {
            formIds.put((String) form.get("formCode"), ((Number) form.get("id")).longValue());
        }
        for (Map<String, Object> item : (List<Map<String, Object>>) detail.getBody().get("items")) {
            Long formId = ((Number) item.get("formId")).longValue();
            itemsByForm.computeIfAbsent(formId, f -> new ArrayList<>())
                    .add(((Number) item.get("id")).longValue());
        }
        assertThat(itemsByForm.get(formIds.get("USER"))).hasSize(36);
        assertThat(itemsByForm.get(formIds.get("DELEGATION"))).hasSize(27);
    }

    @Test
    @Order(2)
    void submittingAllSourcesFeedsTheEngine() {
        submitAdministration("USER", "U-001", 4, Map.of("age", 30, "sex", "M"));
        submitAdministration("USER", "U-002", 2, Map.of("age", 45, "sex", "NB"));
        submitAdministration("STAFF", "S-001", 3, Map.of("age", 38, "sex", "F"));
        submitAdministration("LONG_FORM", "WG-001", 4, null);
        submitAdministration("DELEGATION", "WG-DEL-1", 1, null);
    }

    @Test
    @Order(3)
    void facilityGridsMatchTheHandComputedWorkbookValues() {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/facilities/" + facility1 + "/scores?questionnaireId=" + dtcroId,
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> scores = response.getBody();

        List<Map<String, Object>> items = (List<Map<String, Object>>) scores.get("items");
        assertThat(items).hasSize(45);   // full master grid: 9 areas × a–e

        // 1.a — every source present: U (4+2)/2=3, S 3, Aver-I 3, Com 4, Del 1 → 2.667 INTERMEDIATE
        Map<String, Object> oneA = itemRow(items, "1.a");
        assertThat(num(oneA.get("userMean"))).isEqualTo(3.0);
        assertThat(oneA.get("familyMean")).isNull();
        assertThat(num(oneA.get("staffMean"))).isEqualTo(3.0);
        assertThat(num(oneA.get("averI"))).isEqualTo(3.0);
        assertThat(num(oneA.get("comValue"))).isEqualTo(4.0);
        assertThat(num(oneA.get("delValue"))).isEqualTo(1.0);
        assertThat(num(oneA.get("finalScore"))).isEqualTo(2.667);
        assertThat(oneA.get("classification")).isEqualTo("INTERMEDIATE");

        // 1.c — not on the USER form: userMean null, the average simply skips it
        Map<String, Object> oneC = itemRow(items, "1.c");
        assertThat(oneC.get("userMean")).isNull();
        assertThat(num(oneC.get("finalScore"))).isEqualTo(2.667);

        // 1.d — Delegation covers a–c only: Del null → Final AVG(3,4) = 3.5 EFFICACY
        Map<String, Object> oneD = itemRow(items, "1.d");
        assertThat(oneD.get("delValue")).isNull();
        assertThat(num(oneD.get("finalScore"))).isEqualTo(3.5);
        assertThat(oneD.get("classification")).isEqualTo("EFFICACY");

        // area grid: every area final = AVG(3, 4, 1) = 2.667; facility overall likewise
        List<Map<String, Object>> areas = (List<Map<String, Object>>) scores.get("areas");
        assertThat(areas).hasSize(9);
        for (Map<String, Object> area : areas) {
            assertThat(num(area.get("finalScore"))).as("area %s", area.get("areaCode")).isEqualTo(2.667);
            assertThat(area.get("classification")).isEqualTo("INTERMEDIATE");
        }
        assertThat(num(scores.get("overallScore"))).isEqualTo(2.667);
        assertThat(scores.get("overallClassification")).isEqualTo("INTERMEDIATE");

        // points: all 18 d/e coordinates are strengths (3.5 ≥ 3), nothing is CRITICAL
        List<String> strengths = (List<String>) scores.get("strengthPoints");
        assertThat(strengths).hasSize(18).contains("1.d", "1.e", "9.e");
        assertThat((List<String>) scores.get("weaknessPoints")).isEmpty();
    }

    @Test
    @Order(4)
    void summaryTableCarriesPointsBenchmarksAndEp() {
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/facilities/" + facility1 + "/scores/summary?questionnaireId=" + dtcroId,
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> summary = response.getBody();

        assertThat((List<String>) summary.get("quantStrengthPoints")).hasSize(18).contains("1.d");
        assertThat((List<String>) summary.get("quantWeaknessPoints")).isEmpty();
        // qualitative rows stay null until QCNC exists (register §3.4 note)
        assertThat(summary.get("qualStrengthPoints")).isNull();
        // facility1 is the only scored member → best of its terna in all 9 areas
        assertThat((List<String>) summary.get("benchmarkAreas")).hasSize(9).contains("1", "9");
        // EP = the quantitative strength set (documented assumption); all fall in benchmark areas
        assertThat((List<String>) summary.get("excellencePoints")).hasSize(18);
        assertThat((List<String>) summary.get("epBench")).hasSize(18);
    }

    @Test
    @Order(5)
    void ternaAnalyticsServeBenchmarksAndPopulation() {
        ResponseEntity<List> benchmarks = rest.exchange(
                "/api/v1/ternas/" + ternaId + "/analytics/benchmarks?questionnaireId=" + dtcroId,
                HttpMethod.GET, new HttpEntity<>(admin()), List.class);
        assertThat(benchmarks.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rows = benchmarks.getBody();
        assertThat(rows).hasSize(9);
        for (Map<String, Object> row : rows) {
            assertThat(row.get("bestFacilityCode")).isEqualTo("F-SC-1");
            assertThat(num(row.get("bestScore"))).isEqualTo(2.667);
        }

        ResponseEntity<Map> population = rest.exchange(
                "/api/v1/ternas/" + ternaId + "/analytics/population?questionnaireId=" + dtcroId,
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        assertThat(population.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> facilities = (List<Map<String, Object>>) population.getBody().get("facilities");
        assertThat(facilities).hasSize(3);
        Map<String, Object> f1 = facilities.stream()
                .filter(f -> "F-SC-1".equals(f.get("facilityCode"))).findFirst().orElseThrow();
        Map<String, Map<String, Object>> byCategory = (Map<String, Map<String, Object>>) f1.get("byCategory");
        assertThat(num(byCategory.get("USER").get("collected"))).isEqualTo(2);
        assertThat(num(byCategory.get("USER").get("roster"))).isEqualTo(2);
        assertThat(num(byCategory.get("STAFF").get("collected"))).isEqualTo(1);
        assertThat(num(byCategory.get("FAMILY").get("collected"))).isEqualTo(0);
        assertThat(num(f1.get("collectedTotal"))).isEqualTo(3);
        Map<String, Object> totals = (Map<String, Object>) population.getBody().get("totals");
        assertThat(num(totals.get("collectedTotal"))).isEqualTo(3);
    }

    @Test
    @Order(6)
    void recomputeIsIdempotent() {
        ResponseEntity<Map> recomputed = rest.exchange(
                "/api/v1/facilities/" + facility1 + "/scores/recompute?questionnaireId=" + dtcroId,
                HttpMethod.POST, new HttpEntity<>(admin()), Map.class);
        assertThat(recomputed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(num(recomputed.getBody().get("overallScore"))).isEqualTo(2.667);
        assertThat((List<Map<String, Object>>) recomputed.getBody().get("items")).hasSize(45);
        assertThat((List<String>) recomputed.getBody().get("strengthPoints")).hasSize(18);
    }

    @Test
    @Order(7)
    void outOfScopeFacilityScoresReadAs404() {
        HttpHeaders facilitator2 = headersOf("scfacilitator2", "ScFacilPass!");
        ResponseEntity<Map> foreign = rest.exchange(
                "/api/v1/facilities/" + facility1 + "/scores?questionnaireId=" + dtcroId,
                HttpMethod.GET, new HttpEntity<>(facilitator2), Map.class);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(8)
    void documentApplicationsFeedTheAccreditationHistory() {
        // Terna/Audit coordinators record the applied quality documents (GPP/CGS/EEN)
        ResponseEntity<Map> created = rest.exchange(
                "/api/v1/facilities/" + facility1 + "/document-applications", HttpMethod.POST,
                new HttpEntity<>(Map.of("standardType", "GPP", "standardCode", "GPP CT4-LC",
                        "applicationLevelPct", 40), admin()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> history = rest.exchange(
                "/api/v1/facilities/" + facility1 + "/history", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> cycles = (List<Map<String, Object>>) history.getBody().get("cycles");
        assertThat(cycles).hasSize(1);
        Map<String, Object> cycle = cycles.get(0);
        assertThat(cycle.get("cycleCode")).isEqualTo("2026-2027");
        List<Map<String, Object>> documents = (List<Map<String, Object>>) cycle.get("documentApplications");
        assertThat(documents).extracting(d -> d.get("standardCode")).containsExactly("GPP CT4-LC");
        // the DTCRO Excellence Points of the cycle ride along as coordinate lists
        Map<String, List<String>> ep = (Map<String, List<String>>) cycle.get("excellencePointsByQuestionnaire");
        assertThat(ep.get("DTCRO")).hasSize(18).contains("1.d");
    }
}
