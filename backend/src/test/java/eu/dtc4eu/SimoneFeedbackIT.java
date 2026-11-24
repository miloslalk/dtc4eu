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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simone's feedback of 22 Nov 2026 (FIX-LIST-simone-feedback-2026-11-22.md):
 * ViVaCom on 0–5 with 0 as a real answer, per-questionnaire bands and the
 * scale lock, Internal Facilitator assignment granting access, the
 * "cycles with data" default and the per-role task progress.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SimoneFeedbackIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long facilityId;
    private static Long facilitatorA;
    private static Long facilitatorB;

    private HttpHeaders login(String username, String password) {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password), TokenResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    private HttpHeaders admin() {
        return login("admin", "Dtc4eu!Admin2026");
    }

    private Long createUser(String username, String role) {
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of("username", username,
                "fullName", username, "password", "Feedb4ck!Pass", "roles", List.of(role)), admin()), Map.class);
        ResponseEntity<Map> page = rest.exchange("/api/v1/users?size=100", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        return ((List<Map<String, Object>>) page.getBody().get("content")).stream()
                .filter(u -> username.equals(u.get("username")))
                .map(u -> ((Number) u.get("id")).longValue()).findFirst().orElseThrow();
    }

    private ResponseEntity<Map> setFacilitator(Long userId, boolean sagDone) {
        Map<String, Object> body = new HashMap<>();
        body.put("activity", "VISITING_TERNA");
        body.put("internalFacilitatorId", userId);
        body.put("sagDone", sagDone);
        return rest.exchange("/api/v1/facilities/" + facilityId + "/participation", HttpMethod.POST,
                new HttpEntity<>(body, admin()), Map.class);
    }

    private Map<String, Object> questionnaire(String code) {
        List<Map<String, Object>> all = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class).getBody();
        Number id = (Number) all.stream().filter(q -> code.equals(q.get("code"))).findFirst().orElseThrow().get("id");
        return rest.exchange("/api/v1/questionnaires/" + id, HttpMethod.GET, new HttpEntity<>(admin()), Map.class)
                .getBody();
    }

    @Test
    @Order(1)
    void vivacomIsZeroToFiveAndDtcroKeepsItsBands() {
        Map<String, Object> vivacom = questionnaire("VIVACOM");
        assertThat(vivacom.get("scaleMin")).isEqualTo(0);
        assertThat(vivacom.get("scaleMax")).isEqualTo(5);
        // bands answered by Simone on 24 Nov 2026 (V17, SimoneAnswersIT)
        assertThat(((Number) vivacom.get("bandCriticalMax")).doubleValue()).isEqualTo(3.0);

        Map<String, Object> dtcro = questionnaire("DTCRO");
        assertThat(((Number) dtcro.get("bandCriticalMax")).doubleValue()).isEqualTo(2.0);
        assertThat(((Number) dtcro.get("bandEfficacyMin")).doubleValue()).isEqualTo(3.0);
    }

    @Test
    @Order(2)
    void zeroIsARealAnswerAndTheScaleLocksOnceAnswered() {
        ResponseEntity<Map> created = rest.exchange("/api/v1/questionnaires", HttpMethod.POST, new HttpEntity<>(
                Map.of("code", "ZERO5", "name", "Zero to five", "scaleMin", 0, "scaleMax", 5), admin()), Map.class);
        Number id = (Number) created.getBody().get("id");
        Number areaId = (Number) rest.exchange("/api/v1/questionnaires/" + id + "/areas", HttpMethod.POST,
                new HttpEntity<>(Map.of("areaCode", "A1", "name", "Area one", "sortOrder", 1), admin()), Map.class)
                .getBody().get("id");
        List<Number> itemIds = new java.util.ArrayList<>();
        for (String letter : List.of("a", "b")) {
            itemIds.add((Number) rest.exchange("/api/v1/questionnaires/" + id + "/items", HttpMethod.POST,
                    new HttpEntity<>(Map.of("areaId", areaId, "itemLetter", letter, "itemCode", "Z-1." + letter,
                            "itemText", "Item " + letter, "responseType", "LIKERT"), admin()), Map.class)
                    .getBody().get("id"));
        }

        // bands must sit inside the scale; set them before any answer exists
        ResponseEntity<Map> badBands = rest.exchange("/api/v1/questionnaires/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Zero to five", "bandCriticalMax", 4, "bandEfficacyMin", 3), admin()),
                Map.class);
        assertThat(badBands.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> bands = rest.exchange("/api/v1/questionnaires/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Zero to five", "bandCriticalMax", 2, "bandEfficacyMin", 4), admin()),
                Map.class);
        assertThat(bands.getStatusCode()).isEqualTo(HttpStatus.OK);

        Number responseId = (Number) rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", id, "subjectCode", "Z-001", "respondentCategory", "USER"), admin()), Map.class)
                .getBody().get("id");
        rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT, new HttpEntity<>(Map.of("items",
                List.of(Map.of("itemId", itemIds.get(0), "numericValue", 0),
                        Map.of("itemId", itemIds.get(1), "numericValue", 4))), admin()), Map.class);
        ResponseEntity<List> scores = rest.exchange("/api/v1/responses/" + responseId + "/submit", HttpMethod.POST,
                new HttpEntity<>(admin()), List.class);
        assertThat(scores.getStatusCode()).isEqualTo(HttpStatus.OK);
        // mean of {0, 4} is 2 — the 0 counts, it is not "no answer"
        Map<String, Object> area = (Map<String, Object>) scores.getBody().get(0);
        assertThat(((Number) area.get("meanValue")).doubleValue()).isEqualTo(2.0);

        ResponseEntity<Map> locked = rest.exchange("/api/v1/questionnaires/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Zero to five", "scaleMin", 1), admin()), Map.class);
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<Map> renamed = rest.exchange("/api/v1/questionnaires/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Zero to five (renamed)"), admin()), Map.class);
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody().get("hasResponses")).isEqualTo(true);
    }

    @Test
    @Order(3)
    void assigningAnInternalFacilitatorGrantsAndMovesFacilityAccess() {
        facilityId = ((Number) rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "FB-TC1", "facilityType", "TC", "name", "Feedback TC"), admin()), Map.class)
                .getBody().get("id")).longValue();
        facilitatorA = createUser("fb-facilitator-a", "INTERNAL_FACILITATOR");
        facilitatorB = createUser("fb-facilitator-b", "INTERNAL_FACILITATOR");
        Long evaluator = createUser("fb-evaluator", "EVALUATOR");

        assertThat(setFacilitator(evaluator, false).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        HttpHeaders a = login("fb-facilitator-a", "Feedb4ck!Pass");
        assertThat(rest.exchange("/api/v1/facilities/" + facilityId, HttpMethod.GET, new HttpEntity<>(a), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(setFacilitator(facilitatorA, false).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/v1/facilities/" + facilityId, HttpMethod.GET, new HttpEntity<>(a), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // re-assigning moves the access to the new facilitator
        assertThat(setFacilitator(facilitatorB, false).getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders aAgain = login("fb-facilitator-a", "Feedb4ck!Pass");
        assertThat(rest.exchange("/api/v1/facilities/" + facilityId, HttpMethod.GET, new HttpEntity<>(aAgain),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        HttpHeaders b = login("fb-facilitator-b", "Feedb4ck!Pass");
        assertThat(rest.exchange("/api/v1/facilities/" + facilityId, HttpMethod.GET, new HttpEntity<>(b), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(4)
    void theFacilitatorsTaskListTracksTheChecklist() {
        HttpHeaders b = login("fb-facilitator-b", "Feedb4ck!Pass");
        List<Map<String, Object>> mine = rest.exchange("/api/v1/progress/me", HttpMethod.GET, new HttpEntity<>(b),
                List.class).getBody();
        assertThat(mine).extracting(r -> r.get("facilityCode")).containsExactly("FB-TC1");
        assertThat(taskStatus(mine.get(0), "GAS_SET")).isEqualTo("NOT_STARTED");
        assertThat(mine.get(0).get("internalFacilitatorName")).isEqualTo("fb-facilitator-b");

        setFacilitator(facilitatorB, true);
        List<Map<String, Object>> after = rest.exchange("/api/v1/progress/me", HttpMethod.GET, new HttpEntity<>(b),
                List.class).getBody();
        assertThat(taskStatus(after.get(0), "GAS_SET")).isEqualTo("DONE");
        assertThat(taskStatus(after.get(0), "PARTICIPATION_CHECKLIST")).isEqualTo("IN_PROGRESS");

        assertThat(rest.exchange("/api/v1/progress/ternas/99999", HttpMethod.GET, new HttpEntity<>(admin()),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(5)
    void cyclesWithDataPointFirstTimeVisitorsAtTheirData() {
        List<Number> forAdmin = rest.exchange("/api/v1/auth/me/cycles-with-data", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class).getBody();
        assertThat(forAdmin).isNotEmpty();
        HttpHeaders stranger = login("fb-facilitator-a", "Feedb4ck!Pass");
        List<Number> forStranger = rest.exchange("/api/v1/auth/me/cycles-with-data", HttpMethod.GET,
                new HttpEntity<>(stranger), List.class).getBody();
        assertThat(forStranger).isEmpty();
    }

    private static String taskStatus(Map<String, Object> row, String task) {
        return ((List<Map<String, Object>>) row.get("tasks")).stream()
                .filter(t -> task.equals(t.get("task"))).map(t -> (String) t.get("status")).findFirst().orElseThrow();
    }
}
