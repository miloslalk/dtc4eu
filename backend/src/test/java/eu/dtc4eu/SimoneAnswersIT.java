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
 * Simone's answers of 24 Nov 2026 (WhatsApp, feedback_1): ViVaCom bands and
 * answer labels, one Internal Facilitator community per cycle, Staff and Users
 * in one community per cycle (Family and Managers in several), and the GAS-Set
 * disciplinary area following from the role. QCNC C/NC-only is in FacilityIT,
 * the paper-import side of the community rule in ResponseImportIT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SimoneAnswersIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long facilityA;
    private static Long facilityB;

    private HttpHeaders admin() {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Dtc4eu!Admin2026"), TokenResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    private Long createFacility(String code) {
        return ((Number) rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", code, "facilityType", "TC", "name", code), admin()), Map.class)
                .getBody().get("id")).longValue();
    }

    private Long createFacilitator(String username) {
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of("username", username,
                "fullName", username, "password", "Answ3rs!Pass", "roles", List.of("INTERNAL_FACILITATOR")),
                admin()), Map.class);
        ResponseEntity<Map> page = rest.exchange("/api/v1/users?size=100", HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        return ((List<Map<String, Object>>) page.getBody().get("content")).stream()
                .filter(u -> username.equals(u.get("username")))
                .map(u -> ((Number) u.get("id")).longValue()).findFirst().orElseThrow();
    }

    private ResponseEntity<Map> setFacilitator(Long facilityId, Long userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("activity", "VISITING_TERNA");
        body.put("internalFacilitatorId", userId);
        body.put("sagDone", false);
        return rest.exchange("/api/v1/facilities/" + facilityId + "/participation", HttpMethod.POST,
                new HttpEntity<>(body, admin()), Map.class);
    }

    private ResponseEntity<Map> addMember(Long facilityId, String code, String category) {
        return rest.exchange("/api/v1/facilities/" + facilityId + "/members", HttpMethod.POST,
                new HttpEntity<>(Map.of("memberCode", code, "category", category), admin()), Map.class);
    }

    private Map<String, Object> questionnaire(String code) {
        List<Map<String, Object>> all = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class).getBody();
        Number id = (Number) all.stream().filter(q -> code.equals(q.get("code"))).findFirst().orElseThrow().get("id");
        return rest.exchange("/api/v1/questionnaires/" + id, HttpMethod.GET, new HttpEntity<>(admin()), Map.class)
                .getBody();
    }

    private ResponseEntity<Map> createResponse(Long facilityId, String formCode, String subjectCode) {
        Map<String, Object> dtcro = questionnaire("DTCRO");
        Number formId = (Number) ((List<Map<String, Object>>) dtcro.get("forms")).stream()
                .filter(f -> formCode.equals(f.get("formCode"))).findFirst().orElseThrow().get("id");
        return rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcro.get("id"), "formId", formId, "facilityId", facilityId,
                "subjectCode", subjectCode), admin()), Map.class);
    }

    private static Map<String, Object> scoredAnchorsOnly(Map<String, Object> questionnaire) {
        Map<String, Object> copy = new HashMap<>(questionnaire);
        copy.put("anchors", ((List<Map<String, Object>>) questionnaire.get("anchors")).stream()
                .filter(a -> Boolean.TRUE.equals(a.get("countsInScore"))).toList());
        return copy;
    }

    private static String message(ResponseEntity<Map> response) {
        return String.valueOf(response.getBody().get("message"));
    }

    /** TAB "Punteggio ViVaCom": 0-3 critical, 3-4 intermediate, 4-5 efficacy; Simone's 0-5 labels. */
    @Test
    @Order(1)
    void vivacomHasSimonesBandsAndAnswerLabels() {
        // the scored answers; 8 and 9 (V18, not scored) are checked in SimoneAnswers27IT
        Map<String, Object> vivacom = scoredAnchorsOnly(questionnaire("VIVACOM"));
        assertThat(((Number) vivacom.get("bandCriticalMax")).doubleValue()).isEqualTo(3.0);
        assertThat(((Number) vivacom.get("bandEfficacyMin")).doubleValue()).isEqualTo(4.0);
        assertThat((List<Map<String, Object>>) vivacom.get("anchors"))
                .extracting(a -> ((Number) a.get("value")).intValue(), a -> a.get("label"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, "Nothing or almost nothing is being done, and there"
                                + " are no concrete plans to meet the requirement"),
                        org.assertj.core.groups.Tuple.tuple(1, "There is a written program to meet the requirement,"
                                + " but nothing has been done to meet it"),
                        org.assertj.core.groups.Tuple.tuple(2, "Requirement partially met, but not yet acceptable"),
                        org.assertj.core.groups.Tuple.tuple(3, "Requirement met acceptably (50% of the time)"),
                        org.assertj.core.groups.Tuple.tuple(4, "Requirement met more than acceptably (75% of the"
                                + " time)"),
                        org.assertj.core.groups.Tuple.tuple(5, "Requirement met excellently (90% of the time)"));

        // DTCRO keeps its 1-4 bands (same table) and its official answer labels
        Map<String, Object> dtcro = questionnaire("DTCRO");
        assertThat(((Number) dtcro.get("bandCriticalMax")).doubleValue()).isEqualTo(2.0);
        assertThat(((Number) dtcro.get("bandEfficacyMin")).doubleValue()).isEqualTo(3.0);
    }

    /** "A person can only be an internal facilitator in one community per annual cycle." */
    @Test
    @Order(2)
    void aPersonFacilitatesOnlyOneCommunityPerCycle() {
        facilityA = createFacility("ANS-TC1");
        facilityB = createFacility("ANS-TC2");
        Long facilitator = createFacilitator("ans-facilitator");

        assertThat(setFacilitator(facilityA, facilitator).getStatusCode()).isEqualTo(HttpStatus.OK);
        // saving the same community again is fine
        assertThat(setFacilitator(facilityA, facilitator).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> second = setFacilitator(facilityB, facilitator);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(second)).contains("already the Internal Facilitator of ANS-TC1");

        // once released from the first community, the person can take another
        assertThat(setFacilitator(facilityA, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setFacilitator(facilityB, facilitator).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Staff and Users: one community per cycle. Family members and Managers: several. */
    @Test
    @Order(3)
    void staffAndUsersAreRegisteredInOneCommunityFamilyAndManagersInSeveral() {
        assertThat(addMember(facilityA, "ANS-S1", "STAFF").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> staffTwice = addMember(facilityB, "ANS-S1", "STAFF");
        assertThat(staffTwice.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(message(staffTwice)).contains("ANS-TC1").contains("STAFF");
        // a Staff code cannot reappear elsewhere under another category either
        assertThat(addMember(facilityB, "ans-s1", "FAMILY").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(addMember(facilityA, "ANS-U1", "USER").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addMember(facilityB, "ANS-U1", "USER").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(addMember(facilityA, "ANS-F1", "FAMILY").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addMember(facilityB, "ANS-F1", "FAMILY").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addMember(facilityA, "ANS-M1", "MANAGER").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addMember(facilityB, "ANS-M1", "MANAGER").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // the same code twice in one community is still refused
        ResponseEntity<Map> duplicate = addMember(facilityA, "ANS-F1", "FAMILY");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(message(duplicate)).contains("already registered in this community");
    }

    /** The same rule for answered questionnaires, on create and when a response is moved. */
    @Test
    @Order(4)
    void responsesFollowTheSameCommunityRule() {
        assertThat(createResponse(facilityA, "STAFF", "ANS-S2").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> staffElsewhere = createResponse(facilityB, "STAFF", "ANS-S2");
        assertThat(staffElsewhere.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(message(staffElsewhere)).contains("already takes part in ANS-TC1 as STAFF");

        // a registered User (roster of ANS-TC1) cannot answer for ANS-TC2
        assertThat(createResponse(facilityB, "USER", "ANS-U1").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // nor can a Family member answer for a second community as a User
        assertThat(createResponse(facilityB, "USER", "ANS-F1").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Family members and Managers answer for each of their communities
        assertThat(createResponse(facilityA, "FAMILY", "ANS-F1").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse(facilityB, "FAMILY", "ANS-F1").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse(facilityA, "MANAGER", "ANS-M1").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse(facilityB, "MANAGER", "ANS-M1").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // moving a Staff response to another community is refused; editing it in place is not
        ResponseEntity<Map> staffB = createResponse(facilityB, "STAFF", "ANS-S3");
        assertThat(staffB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number staffBId = (Number) staffB.getBody().get("id");
        ResponseEntity<Map> moved = rest.exchange("/api/v1/responses/" + staffBId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("subjectCode", "ANS-S2", "facilityId", facilityB), admin()), Map.class);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> kept = rest.exchange("/api/v1/responses/" + staffBId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("subjectCode", "ANS-S3", "facilityId", facilityB), admin()), Map.class);
        assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** "Simply choosing the role, the corresponding disciplinary area is automatically assigned." */
    @Test
    @Order(5)
    void theGasSetDisciplinaryAreaFollowsTheRole() {
        Map<String, Object> schema = rest.exchange("/api/v1/facilities/" + facilityA
                + "/set-analysis-grid/schema", HttpMethod.GET, new HttpEntity<>(admin()), Map.class).getBody();
        assertThat(String.valueOf(schema)).contains("derivedFrom");

        Map<String, Object> payload = Map.of(
                "l_team", List.of(
                        Map.of("role", "PL: Psychologist"),
                        Map.of("role", "AS: Social Worker", "disciplinary_area", "A: Other"),
                        Map.of("role", "DC: Delegate Coordinator (operator delegated by the Staff)")),
                "m_staff", List.of(
                        Map.of("role", "I: Nurse"),
                        Map.of("role", "UE: Expert Users / Former Users or Peer Support Operators or Social"
                                + " Facilitators")));
        ResponseEntity<Map> saved = rest.exchange("/api/v1/facilities/" + facilityA + "/set-analysis-grid",
                HttpMethod.PUT, new HttpEntity<>(Map.of("gridVariant", "GAS", "payload", payload), admin()),
                Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> stored = (Map<String, Object>) saved.getBody().get("payload");
        List<Map<String, Object>> team = (List<Map<String, Object>>) stored.get("l_team");
        assertThat(team.get(0).get("disciplinary_area")).isEqualTo("PSI: Psychological-Psychotherapeutic");
        // an area chosen by hand is kept
        assertThat(team.get(1).get("disciplinary_area")).isEqualTo("A: Other");
        // a role with no single area in Table 2 is left for the compiler to choose
        assertThat(team.get(2)).doesNotContainKey("disciplinary_area");
        List<Map<String, Object>> staff = (List<Map<String, Object>>) stored.get("m_staff");
        assertThat(staff.get(0).get("disciplinary_area")).isEqualTo("MSP: Medical-Healthcare-Psychiatric");
        assertThat(staff.get(1).get("disciplinary_area")).isEqualTo("REC: Recovery Orientation");
    }
}
