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
 * Module 5-bis (spec COL-01…03, CORE-04): admin-managed AND/OR rules assign
 * DTCRO forms; the rule test tool previews assignments; a respondent confirms
 * their per-cycle profile and sees only the forms the rules give them, with
 * task status from their own responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssignmentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long dtcroId;
    private static Map<String, Long> formIds = new HashMap<>();
    private static Long userRuleId;

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

    private HttpHeaders respondent() {
        return headersOf("rule-respondent", "RuleResp0nd!");
    }

    private static Map<String, Object> condition(String field, String operator, String... values) {
        return Map.of("type", "condition", "field", field, "operator", operator, "values", List.of(values));
    }

    private static Map<String, Object> group(String op, Object... children) {
        return Map.of("type", "group", "op", op, "children", List.of(children));
    }

    private static Map<String, Object> rule(String name, Map<String, Object> conditions, String formCode,
                                            int priority, boolean active) {
        return Map.of("name", name, "conditions", conditions, "targetFormId", formIds.get(formCode),
                "priority", priority, "active", active);
    }

    @Test
    @Order(1)
    @SuppressWarnings("unchecked")
    void setUpFacilityRespondentAndForms() {
        ResponseEntity<Map> facility = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "RULE-TC1", "facilityType", "TC", "name", "Rule Test TC", "country", "Turkey"),
                admin()), Map.class);
        Number facilityId = (Number) facility.getBody().get("id");
        rest.exchange("/api/v1/facilities/" + facilityId + "/participation", HttpMethod.POST,
                new HttpEntity<>(Map.of("activity", "VISITING_TERNA"), admin()), Map.class);
        ResponseEntity<Map> user = rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "rule-respondent", "fullName", "Rule Respondent", "password", "RuleResp0nd!",
                "roles", List.of("RESPONDENT"), "facilityIds", List.of(facilityId)), admin()), Map.class);
        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<List> questionnaires = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class);
        dtcroId = ((List<Map<String, Object>>) questionnaires.getBody()).stream()
                .filter(q -> "DTCRO".equals(q.get("code"))).map(q -> ((Number) q.get("id")).longValue())
                .findFirst().orElseThrow();
        ResponseEntity<Map> detail = rest.exchange("/api/v1/questionnaires/" + dtcroId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        for (Map<String, Object> form : (List<Map<String, Object>>) detail.getBody().get("forms")) {
            formIds.put((String) form.get("formCode"), ((Number) form.get("id")).longValue());
        }
        assertThat(formIds).containsKeys("USER", "FAMILY", "STAFF");
    }

    @Test
    @Order(2)
    void onlyRuleManagersReachTheRules() {
        ResponseEntity<Map> denied = rest.exchange("/api/v1/assignment-rules", HttpMethod.GET,
                new HttpEntity<>(respondent()), Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> invalid = rest.exchange("/api/v1/assignment-rules", HttpMethod.POST, new HttpEntity<>(
                rule("Broken", condition("CATEGORY", "GTE", "1"), "USER", 0, true), admin()), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) invalid.getBody().get("message")).contains("numeric fields only");
    }

    @Test
    @Order(3)
    @SuppressWarnings("unchecked")
    void rulesAreStoredAndPreviewedWithTheTestTool() {
        ResponseEntity<Map> userRule = rest.exchange("/api/v1/assignment-rules", HttpMethod.POST, new HttpEntity<>(
                rule("Users of TC communities",
                        group("AND", condition("CATEGORY", "IN", "USER"), condition("FACILITY_SETTING", "IN", "TC")),
                        "USER", 10, true), admin()), Map.class);
        assertThat(userRule.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(userRule.getBody().get("targetFormCode")).isEqualTo("USER");
        assertThat(userRule.getBody().get("targetQuestionnaireId")).isEqualTo(dtcroId.intValue());
        userRuleId = ((Number) userRule.getBody().get("id")).longValue();

        rest.exchange("/api/v1/assignment-rules", HttpMethod.POST, new HttpEntity<>(rule("Families, or Poland",
                group("OR", condition("CATEGORY", "IN", "FAMILY"), condition("COUNTRY", "IN", "Poland")),
                "FAMILY", 5, true), admin()), Map.class);
        rest.exchange("/api/v1/assignment-rules", HttpMethod.POST, new HttpEntity<>(rule("Switched off",
                condition("ROLE", "IN", "RESPONDENT"), "STAFF", 99, false), admin()), Map.class);

        ResponseEntity<List> all = rest.exchange("/api/v1/assignment-rules", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class);
        assertThat((List<Map<String, Object>>) all.getBody()).extracting(r -> r.get("name"))
                .containsExactly("Switched off", "Users of TC communities", "Families, or Poland");   // priority desc

        ResponseEntity<Map> preview = rest.exchange("/api/v1/assignment-rules/test", HttpMethod.POST,
                new HttpEntity<>(Map.of("context", Map.of("CATEGORY", List.of("USER"), "FACILITY_SETTING", List.of("TC"),
                        "ROLE", List.of("RESPONDENT"))), admin()), Map.class);
        assertThat((List<Map<String, Object>>) preview.getBody().get("forms"))
                .extracting(f -> f.get("formCode")).containsExactly("USER");   // the inactive STAFF rule is ignored

        ResponseEntity<Map> polish = rest.exchange("/api/v1/assignment-rules/test", HttpMethod.POST,
                new HttpEntity<>(Map.of("context", Map.of("COUNTRY", List.of("poland"))), admin()), Map.class);
        assertThat((List<Map<String, Object>>) polish.getBody().get("forms"))
                .extracting(f -> f.get("formCode")).containsExactly("FAMILY");

        ResponseEntity<Map> expired = rest.exchange("/api/v1/assignment-rules/" + userRuleId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "Users of TC communities", "conditions", condition("CATEGORY", "IN", "USER"),
                        "targetFormId", formIds.get("USER"), "priority", 10, "validFrom", "2030-01-01",
                        "validTo", "2029-01-01"), admin()), Map.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(4)
    @SuppressWarnings("unchecked")
    void respondentSeesOnlyTheFormsTheirProfileEarns() {
        // no profile yet: the facility context alone (TC, Turkey) earns nothing
        ResponseEntity<Map> before = rest.exchange("/api/v1/me/assignments", HttpMethod.GET,
                new HttpEntity<>(respondent()), Map.class);
        assertThat(before.getBody().get("profileComplete")).isEqualTo(false);
        assertThat((List<?>) before.getBody().get("tasks")).isEmpty();
        Map<String, List<String>> context = (Map<String, List<String>>) before.getBody().get("context");
        assertThat(context.get("FACILITY_SETTING")).containsExactly("TC");
        assertThat(context.get("COUNTRY")).containsExactly("Turkey");
        assertThat(context.get("PROJECT_PHASE")).containsExactly("VISITING_TERNA");

        ResponseEntity<Map> badProfile = rest.exchange("/api/v1/me/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("roleCategory", "PRESIDENT"), respondent()), Map.class);
        assertThat(badProfile.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> profile = rest.exchange("/api/v1/me/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("roleCategory", "USER", "age", 34), respondent()), Map.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().get("roleCategory")).isEqualTo("USER");
        assertThat((List<Map<String, Object>>) profile.getBody().get("facilities"))
                .extracting(f -> f.get("facilityCode")).containsExactly("RULE-TC1");

        ResponseEntity<Map> assigned = rest.exchange("/api/v1/me/assignments", HttpMethod.GET,
                new HttpEntity<>(respondent()), Map.class);
        assertThat(assigned.getBody().get("profileComplete")).isEqualTo(true);
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) assigned.getBody().get("tasks");
        assertThat(tasks).hasSize(1);
        assertThat(((Map<String, Object>) tasks.get(0).get("form")).get("formCode")).isEqualTo("USER");
        assertThat(tasks.get(0).get("status")).isEqualTo("NOT_STARTED");

        // starting the form turns the task into a draft pointing at the response
        ResponseEntity<Map> started = rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcroId, "formId", formIds.get("USER"), "subjectCode", "RULE-SUBJ-1"),
                respondent()), Map.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> after = rest.exchange("/api/v1/me/assignments", HttpMethod.GET,
                new HttpEntity<>(respondent()), Map.class);
        Map<String, Object> task = ((List<Map<String, Object>>) after.getBody().get("tasks")).get(0);
        assertThat(task.get("status")).isEqualTo("DRAFT");
        assertThat(((Number) task.get("responseId")).longValue())
                .isEqualTo(((Number) started.getBody().get("id")).longValue());
    }

    @Test
    @Order(5)
    void deletingARuleWithdrawsTheAssignment() {
        ResponseEntity<Void> deleted = rest.exchange("/api/v1/assignment-rules/" + userRuleId, HttpMethod.DELETE,
                new HttpEntity<>(admin()), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> after = rest.exchange("/api/v1/me/assignments", HttpMethod.GET,
                new HttpEntity<>(respondent()), Map.class);
        assertThat((List<?>) after.getBody().get("tasks")).isEmpty();

        ResponseEntity<Map> missing = rest.exchange("/api/v1/assignment-rules/" + userRuleId, HttpMethod.DELETE,
                new HttpEntity<>(admin()), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
