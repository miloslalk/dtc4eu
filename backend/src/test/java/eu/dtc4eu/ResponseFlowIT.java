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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T5 acceptance (spec §7): a respondent completes and submits a questionnaire;
 * area means are computed correctly; submitted responses are read-only; the
 * research role can list, view scores and export raw item-level data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResponseFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long facilityId;
    private static Long dtcroId;
    private static Long responseId;
    private static List<Map<String, Object>> dtcroItems;

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
    void setUpFacilityFacilitatorAndLoadDtcro() {
        ResponseEntity<Map> facility = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "T5-F1", "facilityType", "TC", "name", "Response Flow TC"), admin()), Map.class);
        facilityId = ((Number) facility.getBody().get("id")).longValue();

        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "t5facilitator", "fullName", "T5 Facilitator", "password", "T5facilPass!",
                "roles", List.of("INTERNAL_FACILITATOR"), "facilityIds", List.of(facilityId)), admin()), Map.class);
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "t5research", "fullName", "T5 Research", "password", "T5resePass!",
                "roles", List.of("RESEARCH_TEAM")), admin()), Map.class);
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "t5respondent", "fullName", "T5 Respondent", "password", "T5respPass!",
                "roles", List.of("RESPONDENT")), admin()), Map.class);

        // the DTCRO placeholder seed (V4) provides 9 areas × 5 items
        ResponseEntity<List> questionnaires = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class);
        Map<String, Object> dtcro = ((List<Map<String, Object>>) questionnaires.getBody()).stream()
                .filter(q -> "DTCRO".equals(q.get("code"))).findFirst().orElseThrow();
        dtcroId = ((Number) dtcro.get("id")).longValue();
        assertThat(dtcro.get("itemCount")).isEqualTo(45);
        assertThat(dtcro.get("areaCount")).isEqualTo(9);

        ResponseEntity<Map> detail = rest.exchange("/api/v1/questionnaires/" + dtcroId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        dtcroItems = (List<Map<String, Object>>) detail.getBody().get("items");
        assertThat(dtcroItems).hasSize(45);
    }

    @Test
    @Order(2)
    void facilitatorOpensAResponseAndAutosavesDraftAnswers() {
        HttpHeaders facilitator = headersOf("t5facilitator", "T5facilPass!");

        ResponseEntity<Map> created = rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcroId, "facilityId", facilityId, "subjectCode", "SUBJ-001",
                "respondentCategory", "USERS"), facilitator), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        responseId = ((Number) created.getBody().get("id")).longValue();
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");
        assertThat(((Number) created.getBody().get("totalLikert")).intValue()).isEqualTo(45);

        // autosave a partial batch (first 10 items = 2 full areas), value pattern: 1,2,3,4,1,2,3,4,...
        List<Map<String, Object>> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            batch.add(Map.of("itemId", dtcroItems.get(i).get("id"), "numericValue", (i % 4) + 1));
        }
        ResponseEntity<Map> saved = rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", batch), facilitator), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) saved.getBody().get("answeredLikert")).intValue()).isEqualTo(10);

        // out-of-scale Likert value → 400
        ResponseEntity<Map> outOfScale = rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", List.of(Map.of(
                        "itemId", dtcroItems.get(0).get("id"), "numericValue", 7))), facilitator), Map.class);
        assertThat(outOfScale.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(3)
    void submitIsRejectedWhileRequiredItemsAreMissing() {
        HttpHeaders facilitator = headersOf("t5facilitator", "T5facilPass!");

        ResponseEntity<Map> premature = rest.exchange("/api/v1/responses/" + responseId + "/submit",
                HttpMethod.POST, new HttpEntity<>(facilitator), Map.class);

        assertThat(premature.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) premature.getBody().get("message")).contains("not answered");
    }

    @Test
    @Order(4)
    void submitComputesCorrectAreaMeansAndLocksTheResponse() {
        HttpHeaders facilitator = headersOf("t5facilitator", "T5facilPass!");

        // answer ALL 45 items with the repeating pattern 1,2,3,4,1,... per item index.
        // Each area holds 5 consecutive items; expected means per area:
        //   area k (0-based) items i=5k..5k+4, value = (i % 4) + 1
        List<Map<String, Object>> all = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            all.add(Map.of("itemId", dtcroItems.get(i).get("id"), "numericValue", (i % 4) + 1));
        }
        rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", all), facilitator), Map.class);

        ResponseEntity<List> submit = rest.exchange("/api/v1/responses/" + responseId + "/submit",
                HttpMethod.POST, new HttpEntity<>(facilitator), List.class);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> scores = submit.getBody();
        assertThat(scores).hasSize(9);

        // verify the mean of every area against the pattern
        Map<String, Double> expected = new HashMap<>();
        for (int area = 0; area < 9; area++) {
            double sum = 0;
            for (int i = area * 5; i < area * 5 + 5; i++) {
                sum += (i % 4) + 1;
            }
            expected.put("A" + (area + 1), sum / 5.0);
        }
        for (Map<String, Object> score : scores) {
            String areaCode = (String) score.get("areaCode");
            double mean = Double.parseDouble(String.valueOf(score.get("meanValue")));
            assertThat(mean).as("mean of %s", areaCode).isEqualTo(expected.get(areaCode));
        }

        // submitted response is read-only: item writes and header updates → 409
        ResponseEntity<Map> lockedItems = rest.exchange("/api/v1/responses/" + responseId + "/items",
                HttpMethod.PUT, new HttpEntity<>(Map.of("items", List.of(Map.of(
                        "itemId", dtcroItems.get(0).get("id"), "numericValue", 1))), facilitator), Map.class);
        assertThat(lockedItems.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> lockedHeader = rest.exchange("/api/v1/responses/" + responseId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("subjectCode", "SUBJ-001", "respondentCategory", "USERS"), facilitator),
                Map.class);
        assertThat(lockedHeader.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> resubmit = rest.exchange("/api/v1/responses/" + responseId + "/submit",
                HttpMethod.POST, new HttpEntity<>(facilitator), Map.class);
        assertThat(resubmit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(5)
    void respondentSeesOnlyOwnResponses() {
        HttpHeaders respondent = headersOf("t5respondent", "T5respPass!");

        // sees nothing initially
        ResponseEntity<Map> before = rest.exchange("/api/v1/responses", HttpMethod.GET,
                new HttpEntity<>(respondent), Map.class);
        assertThat((List<?>) before.getBody().get("content")).isEmpty();

        // the facilitator's response is invisible to them (404, not 403)
        ResponseEntity<Map> foreign = rest.exchange("/api/v1/responses/" + responseId, HttpMethod.GET,
                new HttpEntity<>(respondent), Map.class);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // creates own response and sees exactly it
        ResponseEntity<Map> own = rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcroId, "subjectCode", "SUBJ-SELF",
                "respondentCategory", "USERS"), respondent), Map.class);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> after = rest.exchange("/api/v1/responses", HttpMethod.GET,
                new HttpEntity<>(respondent), Map.class);
        List<Map<String, Object>> content = (List<Map<String, Object>>) after.getBody().get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("subjectCode")).isEqualTo("SUBJ-SELF");
    }

    @Test
    @Order(6)
    void researchRoleListsViewsScoresAndExportsRawItems() {
        HttpHeaders research = headersOf("t5research", "T5resePass!");

        ResponseEntity<Map> list = rest.exchange("/api/v1/responses?status=SUBMITTED", HttpMethod.GET,
                new HttpEntity<>(research), Map.class);
        assertThat((List<?>) list.getBody().get("content")).hasSize(1);

        ResponseEntity<List> scores = rest.exchange("/api/v1/responses/" + responseId + "/scores",
                HttpMethod.GET, new HttpEntity<>(research), List.class);
        assertThat(scores.getBody()).hasSize(9);

        ResponseEntity<String> export = rest.exchange("/api/v1/responses/export?status=SUBMITTED",
                HttpMethod.GET, new HttpEntity<>(research), String.class);
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(export.getHeaders().getContentType().toString()).contains("text/csv");
        // header + 45 item rows
        assertThat(export.getBody().lines().count()).isEqualTo(46);
        assertThat(export.getBody()).contains("SUBJ-001").contains("D01").contains("A1");

        // facilitator (non-research) cannot export
        ResponseEntity<String> forbidden = rest.exchange("/api/v1/responses/export", HttpMethod.GET,
                new HttpEntity<>(headersOf("t5facilitator", "T5facilPass!")), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
