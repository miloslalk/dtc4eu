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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2 acceptance (spec §7): an internal facilitator sees only their facility;
 * members recordable in the four categories with codes; participation settable
 * per cycle; list filters and CSV export work.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FacilityIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long facilityAId;
    private static Long facilityBId;

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
    void adminCreatesTwoFacilities() {
        ResponseEntity<Map> a = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "F-IT-01", "facilityType", "TC", "name", "Therapeutic Community Alpha",
                "country", "Italy", "city", "Rome"), admin()), Map.class);
        ResponseEntity<Map> b = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "F-GR-02", "facilityType", "GA", "name", "Group Apartment Beta",
                "country", "Greece", "city", "Athens"), admin()), Map.class);

        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(b.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        facilityAId = ((Number) a.getBody().get("id")).longValue();
        facilityBId = ((Number) b.getBody().get("id")).longValue();

        // duplicate code → 409
        ResponseEntity<Map> dup = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "F-IT-01", "facilityType", "TC", "name", "Dup"), admin()), Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(2)
    void facilitatorScopedToFacilityASeesOnlyIt() {
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "facilitatorA", "fullName", "Facilitator A", "password", "Facil1tatorA!",
                "roles", List.of("INTERNAL_FACILITATOR"), "facilityIds", List.of(facilityAId),
                "ternaIds", List.of()), admin()), Map.class);

        HttpHeaders facilitator = headersOf("facilitatorA", "Facil1tatorA!");

        ResponseEntity<Map> list = rest.exchange("/api/v1/facilities", HttpMethod.GET,
                new HttpEntity<>(facilitator), Map.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) list.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("facilityCode")).isEqualTo("F-IT-01");

        // out-of-scope facility must be a 404, not 403 (don't reveal existence)
        ResponseEntity<Map> other = rest.exchange("/api/v1/facilities/" + facilityBId, HttpMethod.GET,
                new HttpEntity<>(facilitator), Map.class);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(3)
    void participationIsSettablePerCycleAndUpserts() {
        HttpHeaders facilitator = headersOf("facilitatorA", "Facil1tatorA!");

        ResponseEntity<Map> set = rest.exchange("/api/v1/facilities/" + facilityAId + "/participation",
                HttpMethod.POST, new HttpEntity<>(Map.of("activity", "VISITING_TERNA"), facilitator), Map.class);
        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);

        // upsert: switching activity for the same cycle replaces, not duplicates
        ResponseEntity<Map> replaced = rest.exchange("/api/v1/facilities/" + facilityAId + "/participation",
                HttpMethod.POST, new HttpEntity<>(Map.of("activity", "QUALITY_AUDIT"), facilitator), Map.class);
        assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replaced.getBody().get("activity")).isEqualTo("QUALITY_AUDIT");

        ResponseEntity<Map> get = rest.exchange("/api/v1/facilities/" + facilityAId + "/participation",
                HttpMethod.GET, new HttpEntity<>(facilitator), Map.class);
        assertThat(get.getBody().get("activity")).isEqualTo("QUALITY_AUDIT");
    }

    @Test
    @Order(4)
    void membersRecordedInCategoriesWithUniqueCodesPerCycle() {
        HttpHeaders facilitator = headersOf("facilitatorA", "Facil1tatorA!");
        String base = "/api/v1/facilities/" + facilityAId + "/members";

        for (String[] member : new String[][]{{"U-001", "USER"}, {"FAM-001", "FAMILY"},
                {"PRA-001", "STAFF"}, {"MAN-001", "MANAGER"}}) {
            ResponseEntity<Map> added = rest.exchange(base, HttpMethod.POST,
                    new HttpEntity<>(Map.of("memberCode", member[0], "category", member[1]), facilitator), Map.class);
            assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<Map> duplicate = rest.exchange(base, HttpMethod.POST,
                new HttpEntity<>(Map.of("memberCode", "U-001", "category", "USER"), facilitator), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<List> members = rest.exchange(base, HttpMethod.GET, new HttpEntity<>(facilitator), List.class);
        assertThat(members.getBody()).hasSize(4);
    }

    @Test
    @Order(5)
    void filtersAndCsvExportWork() {
        ResponseEntity<Map> byCountry = rest.exchange("/api/v1/facilities?country=Italy", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) byCountry.getBody().get("content");
        assertThat(content).extracting(m -> m.get("facilityCode")).contains("F-IT-01").doesNotContain("F-GR-02");

        ResponseEntity<Map> byActivity = rest.exchange("/api/v1/facilities?activity=QUALITY_AUDIT", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        List<Map<String, Object>> activityContent = (List<Map<String, Object>>) byActivity.getBody().get("content");
        assertThat(activityContent).extracting(m -> m.get("facilityCode")).contains("F-IT-01");

        ResponseEntity<String> csv = rest.exchange("/api/v1/facilities/export?type=TC", HttpMethod.GET,
                new HttpEntity<>(admin()), String.class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(csv.getBody()).contains("facility_code").contains("F-IT-01").doesNotContain("F-GR-02");
    }

    @Test
    @Order(6)
    void sagAndQcncUpsertPerCycle() {
        HttpHeaders facilitator = headersOf("facilitatorA", "Facil1tatorA!");

        ResponseEntity<Map> sag = rest.exchange("/api/v1/facilities/" + facilityAId + "/set-analysis-grid",
                HttpMethod.PUT, new HttpEntity<>(Map.of("gridVariant", "A",
                        "payload", Map.of("q1", "answer")), facilitator), Map.class);
        assertThat(sag.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> qcnc = rest.exchange("/api/v1/facilities/" + facilityAId + "/qcnc",
                HttpMethod.PUT, new HttpEntity<>(Map.of("payload", Map.of("Q1", "3", "Q2", "2")), facilitator),
                Map.class);
        assertThat(qcnc.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> sags = rest.exchange("/api/v1/facilities/" + facilityAId + "/set-analysis-grid",
                HttpMethod.GET, new HttpEntity<>(facilitator), List.class);
        assertThat(sags.getBody()).hasSize(1);
    }

    @Test
    @Order(7)
    void closedCycleRejectsWrites() {
        // create + close a cycle, then try to write against it via X-Cycle-Id
        ResponseEntity<Map> created = rest.exchange("/api/v1/cycles", HttpMethod.POST,
                new HttpEntity<>(Map.of("code", "2031-2032"), admin()), Map.class);
        Number closedCycleId = (Number) created.getBody().get("id");
        rest.exchange("/api/v1/cycles/" + closedCycleId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", "CLOSED"), admin()), Map.class);

        HttpHeaders headers = admin();
        headers.set("X-Cycle-Id", String.valueOf(closedCycleId));
        ResponseEntity<Map> write = rest.exchange("/api/v1/facilities/" + facilityAId + "/participation",
                HttpMethod.POST, new HttpEntity<>(Map.of("activity", "VISITING_TERNA"), headers), Map.class);

        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) write.getBody().get("message")).contains("read-only");

        // reads against the closed cycle still work
        ResponseEntity<Map> read = rest.exchange("/api/v1/facilities/" + facilityAId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Expert feedback (prototype annotations): each QCNC mark carries a 1–4
     * score; the board reports the mean across administrations with its TAB.5
     * classification — the intermediate band only exists through that mean.
     */
    @Test
    @Order(8)
    @SuppressWarnings("unchecked")
    void qcncStandardScoresProduceMeanAndClassification() {
        ResponseEntity<Map> board = rest.exchange("/api/v1/facilities/" + facilityAId + "/qcnc-standards",
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        assertThat(board.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> standards = (List<Map<String, Object>>) board.getBody().get("standards");
        assertThat(standards).isNotEmpty(); // V11 catalogue
        Number standardId = (Number) standards.get(0).get("id");

        HttpHeaders facilitator = headersOf("facilitatorA", "Facil1tatorA!");
        ResponseEntity<Map> self = rest.exchange(
                "/api/v1/facilities/" + facilityAId + "/qcnc-standards/INTERNAL_FACILITATOR", HttpMethod.PUT,
                new HttpEntity<>(Map.of("results", List.of(Map.of(
                        "standardId", standardId, "conformity", "C", "score", 4))), facilitator), Map.class);
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> own = rest.exchange(
                "/api/v1/facilities/" + facilityAId + "/qcnc-standards/TERNA_COORDINATOR_OWN", HttpMethod.PUT,
                new HttpEntity<>(Map.of("results", List.of(Map.of(
                        "standardId", standardId, "conformity", "NC", "score", 1))), admin()), Map.class);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);

        // mean(4, 1) = 2.500 → INTERMEDIATE
        List<Map<String, Object>> scores = (List<Map<String, Object>>) own.getBody().get("standardScores");
        Map<String, Object> row = scores.stream()
                .filter(s -> ((Number) s.get("standardId")).longValue() == standardId.longValue())
                .findFirst().orElseThrow();
        assertThat(new BigDecimal(String.valueOf(row.get("meanScore")))).isEqualByComparingTo("2.5");
        assertThat(row.get("classification")).isEqualTo("INTERMEDIATE");

        // scores outside 1–4 are rejected by validation
        ResponseEntity<Map> invalid = rest.exchange(
                "/api/v1/facilities/" + facilityAId + "/qcnc-standards/INTERNAL_FACILITATOR", HttpMethod.PUT,
                new HttpEntity<>(Map.of("results", List.of(Map.of(
                        "standardId", standardId, "conformity", "C", "score", 5))), facilitator), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Clearing a score (null) stores null and drops that administration from
     * the skip-missing mean; a standard with marks but no scores has no mean.
     * Builds on @Order(8): standard 0 scored 4 (facilitator) and 1 (coordinator).
     */
    @Test
    @Order(9)
    @SuppressWarnings("unchecked")
    void qcncClearedScoreLeavesTheMean() {
        ResponseEntity<Map> board = rest.exchange("/api/v1/facilities/" + facilityAId + "/qcnc-standards",
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        List<Map<String, Object>> standards = (List<Map<String, Object>>) board.getBody().get("standards");
        long scoredId = ((Number) standards.get(0).get("id")).longValue();
        long unscoredId = ((Number) standards.get(1).get("id")).longValue();

        Map<String, Object> cleared = new HashMap<>();
        cleared.put("standardId", scoredId);
        cleared.put("conformity", "C");
        cleared.put("score", null);
        ResponseEntity<Map> saved = rest.exchange(
                "/api/v1/facilities/" + facilityAId + "/qcnc-standards/INTERNAL_FACILITATOR", HttpMethod.PUT,
                new HttpEntity<>(Map.of("results", List.of(cleared,
                        Map.of("standardId", unscoredId, "conformity", "NC"))),
                        headersOf("facilitatorA", "Facil1tatorA!")), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> facilitatorColumn = ((List<Map<String, Object>>) saved.getBody().get("administrations"))
                .stream().filter(a -> "INTERNAL_FACILITATOR".equals(a.get("administrationType")))
                .findFirst().orElseThrow();
        assertThat((Map<String, Object>) facilitatorColumn.get("conformityByStandard"))
                .containsKeys(String.valueOf(scoredId), String.valueOf(unscoredId));
        assertThat((Map<String, Object>) facilitatorColumn.get("scoreByStandard")).isEmpty();

        // only the coordinator's 1 remains → mean 1.000, CRITICAL; the unscored standard has no row
        List<Map<String, Object>> scores = (List<Map<String, Object>>) saved.getBody().get("standardScores");
        assertThat(scores).noneMatch(s -> ((Number) s.get("standardId")).longValue() == unscoredId);
        Map<String, Object> row = scores.stream()
                .filter(s -> ((Number) s.get("standardId")).longValue() == scoredId)
                .findFirst().orElseThrow();
        assertThat(new BigDecimal(String.valueOf(row.get("meanScore")))).isEqualByComparingTo("1");
        assertThat(row.get("classification")).isEqualTo("CRITICAL");
    }
}
