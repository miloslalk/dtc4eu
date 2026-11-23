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
 * T5 acceptance, spec v0.2: responses are opened against one of the six DTCRO
 * forms (V7 item bank, 216 items); the profile header (register §2.4) is
 * required at submit; per-response area means still compute; submitted
 * responses are read-only; research exports raw items with coordinates.
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
    private static Long staffFormId;
    private static Long userFormId;
    private static Long responseId;
    private static List<Map<String, Object>> staffItems;

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
    void setUpFacilityFacilitatorAndLoadDtcroItemBank() {
        ResponseEntity<Map> facility = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "T5-F1", "facilityType", "TC", "facilitySubtype", "RTC",
                "name", "Response Flow TC"), admin()), Map.class);
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

        // the V7 item bank: 9 canonical areas, six forms, 216 per-form items
        ResponseEntity<List> questionnaires = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class);
        Map<String, Object> dtcro = ((List<Map<String, Object>>) questionnaires.getBody()).stream()
                .filter(q -> "DTCRO".equals(q.get("code"))).findFirst().orElseThrow();
        dtcroId = ((Number) dtcro.get("id")).longValue();
        assertThat(dtcro.get("itemCount")).isEqualTo(216);
        assertThat(dtcro.get("areaCount")).isEqualTo(9);
        assertThat(dtcro.get("formCount")).isEqualTo(6);

        ResponseEntity<Map> detail = rest.exchange("/api/v1/questionnaires/" + dtcroId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        List<Map<String, Object>> forms = (List<Map<String, Object>>) detail.getBody().get("forms");
        staffFormId = formId(forms, "STAFF");
        userFormId = formId(forms, "USER");

        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.getBody().get("items");
        assertThat(items).hasSize(216);
        staffItems = items.stream()
                .filter(i -> Objects.equals(((Number) i.get("formId")).longValue(), staffFormId))
                .toList();
        assertThat(staffItems).hasSize(45);
        long userItems = items.stream()
                .filter(i -> Objects.equals(((Number) i.get("formId")).longValue(), userFormId)).count();
        assertThat(userItems).isEqualTo(36);

        // anchors delivered with the definition (register §2.3)
        List<Map<String, Object>> anchors = (List<Map<String, Object>>) detail.getBody().get("anchors");
        assertThat(anchors).extracting(a -> a.get("label"))
                .containsExactly("Not at all", "A little", "A lot", "Very much");
    }

    private static Long formId(List<Map<String, Object>> forms, String code) {
        return forms.stream().filter(f -> code.equals(f.get("formCode")))
                .map(f -> ((Number) f.get("id")).longValue()).findFirst().orElseThrow();
    }

    @Test
    @Order(2)
    void facilitatorOpensAFormBoundResponseAndAutosavesDraftAnswers() {
        HttpHeaders facilitator = headersOf("t5facilitator", "T5facilPass!");

        // form-less creation is rejected: DTCRO is administered through forms
        ResponseEntity<Map> noForm = rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcroId, "facilityId", facilityId, "subjectCode", "SUBJ-000",
                "respondentCategory", "USER"), facilitator), Map.class);
        assertThat(noForm.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> created = rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcroId, "formId", staffFormId, "facilityId", facilityId,
                "subjectCode", "SUBJ-001"), facilitator), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        responseId = ((Number) created.getBody().get("id")).longValue();
        assertThat(created.getBody().get("status")).isEqualTo("DRAFT");
        // category + mode derive from the form
        assertThat(created.getBody().get("respondentCategory")).isEqualTo("STAFF");
        assertThat(created.getBody().get("administrationMode")).isEqualTo("INDIVIDUAL");
        assertThat(((Number) created.getBody().get("totalLikert")).intValue()).isEqualTo(45);

        // autosave a partial batch (first 10 items = 2 full areas), value pattern 1,2,3,4,1,...
        List<Map<String, Object>> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            batch.add(Map.of("itemId", staffItems.get(i).get("id"), "numericValue", (i % 4) + 1));
        }
        ResponseEntity<Map> saved = rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", batch), facilitator), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) saved.getBody().get("answeredLikert")).intValue()).isEqualTo(10);

        // out-of-scale Likert value → 400
        ResponseEntity<Map> outOfScale = rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", List.of(Map.of(
                        "itemId", staffItems.get(0).get("id"), "numericValue", 7))), facilitator), Map.class);
        assertThat(outOfScale.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // an item of ANOTHER form is rejected (form-scoped definition)
        ResponseEntity<Map> detail = rest.exchange("/api/v1/questionnaires/" + dtcroId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        Number foreignItemId = (Number) ((List<Map<String, Object>>) detail.getBody().get("items")).stream()
                .filter(i -> Objects.equals(((Number) i.get("formId")).longValue(), userFormId))
                .findFirst().orElseThrow().get("id");
        ResponseEntity<Map> foreign = rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", List.of(Map.of(
                        "itemId", foreignItemId, "numericValue", 2))), facilitator), Map.class);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
    void submitRequiresTheProfileThenComputesAreaMeansAndLocksTheResponse() {
        HttpHeaders facilitator = headersOf("t5facilitator", "T5facilPass!");

        // answer ALL 45 STAFF items with the repeating pattern 1,2,3,4,1,... per item index.
        // Items arrive area-ordered (letters a–e per area), so area k holds items 5k..5k+4.
        List<Map<String, Object>> all = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            all.add(Map.of("itemId", staffItems.get(i).get("id"), "numericValue", (i % 4) + 1));
        }
        rest.exchange("/api/v1/responses/" + responseId + "/items", HttpMethod.PUT,
                new HttpEntity<>(Map.of("items", all), facilitator), Map.class);

        // items complete but the profile header (register §2.4) is missing → 400
        ResponseEntity<Map> noProfile = rest.exchange("/api/v1/responses/" + responseId + "/submit",
                HttpMethod.POST, new HttpEntity<>(facilitator), Map.class);
        assertThat(noProfile.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) noProfile.getBody().get("message")).contains("Profile");

        // an unknown profile key is rejected outright
        ResponseEntity<Map> badProfile = rest.exchange("/api/v1/responses/" + responseId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("subjectCode", "SUBJ-001", "facilityId", facilityId,
                        "profileSnapshot", Map.of("age", 40, "sex", "F", "shoe_size", 42)), facilitator), Map.class);
        assertThat(badProfile.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> withProfile = rest.exchange("/api/v1/responses/" + responseId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("subjectCode", "SUBJ-001", "facilityId", facilityId,
                        "profileSnapshot", Map.of("age", 40, "sex", "F")), facilitator), Map.class);
        assertThat(withProfile.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List> submit = rest.exchange("/api/v1/responses/" + responseId + "/submit",
                HttpMethod.POST, new HttpEntity<>(facilitator), List.class);
        assertThat(submit.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> scores = submit.getBody();
        assertThat(scores).hasSize(9);

        // verify the mean of every canonical area R1..R9 against the pattern
        Map<String, Double> expected = new HashMap<>();
        for (int area = 0; area < 9; area++) {
            double sum = 0;
            for (int i = area * 5; i < area * 5 + 5; i++) {
                sum += (i % 4) + 1;
            }
            expected.put("R" + (area + 1), sum / 5.0);
        }
        for (Map<String, Object> score : scores) {
            String areaCode = (String) score.get("areaCode");
            double mean = Double.parseDouble(String.valueOf(score.get("meanValue")));
            assertThat(mean).as("mean of %s", areaCode).isEqualTo(expected.get(areaCode));
        }

        // submitted response is read-only: item writes and header updates → 409
        ResponseEntity<Map> lockedItems = rest.exchange("/api/v1/responses/" + responseId + "/items",
                HttpMethod.PUT, new HttpEntity<>(Map.of("items", List.of(Map.of(
                        "itemId", staffItems.get(0).get("id"), "numericValue", 1))), facilitator), Map.class);
        assertThat(lockedItems.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> lockedHeader = rest.exchange("/api/v1/responses/" + responseId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("subjectCode", "SUBJ-001"), facilitator), Map.class);
        assertThat(lockedHeader.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> resubmit = rest.exchange("/api/v1/responses/" + responseId + "/submit",
                HttpMethod.POST, new HttpEntity<>(facilitator), Map.class);
        assertThat(resubmit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // spec INS-06: an item with stored answers is frozen, even for an admin
        Map<String, Object> answered = new java.util.HashMap<>(staffItems.get(0));
        answered.put("itemText", "Reworded after responses exist");
        ResponseEntity<Map> frozenItem = rest.exchange(
                "/api/v1/questionnaires/" + dtcroId + "/items/" + staffItems.get(0).get("id"),
                HttpMethod.PUT, new HttpEntity<>(answered, admin()), Map.class);
        assertThat(frozenItem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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

        // creates their own USER-form response and sees exactly it
        ResponseEntity<Map> own = rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcroId, "formId", userFormId, "subjectCode", "SUBJ-SELF"), respondent), Map.class);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(own.getBody().get("respondentCategory")).isEqualTo("USER");
        assertThat(((Number) own.getBody().get("totalLikert")).intValue()).isEqualTo(36);

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
        // header + 45 STAFF item rows, with form code, coordinates and canonical areas
        assertThat(export.getBody().lines().count()).isEqualTo(46);
        assertThat(export.getBody()).contains("SUBJ-001").contains("STAFF-1.a").contains("R1").contains("STAFF");

        // facilitator (non-research) cannot export
        ResponseEntity<String> forbidden = rest.exchange("/api/v1/responses/export", HttpMethod.GET,
                new HttpEntity<>(headersOf("t5facilitator", "T5facilPass!")), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
