package eu.dtc4eu;

import eu.dtc4eu.questionnaire.entity.ResponseAreaScore;
import eu.dtc4eu.questionnaire.service.ScoringService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Simone's answers of 27 Nov 2026 (WhatsApp): QCNC 1A-1C count for the Q1
 * meta-areas, the GAS-Set area of RA is "Other" and Volunteers have none, and
 * ViVaCom answers 8 and 9 are recorded but not scored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SimoneAnswers27IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScoringService scoringService;

    private static Long facility;

    private HttpHeaders admin() {
        TokenResponse tokens = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Dtc4eu!Admin2026"), TokenResponse.class).getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return headers;
    }

    private Map<String, Object> questionnaire(String code) {
        List<Map<String, Object>> all = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class).getBody();
        Number id = (Number) all.stream().filter(q -> code.equals(q.get("code"))).findFirst().orElseThrow().get("id");
        return rest.exchange("/api/v1/questionnaires/" + id, HttpMethod.GET, new HttpEntity<>(admin()), Map.class)
                .getBody();
    }

    private Long facility() {
        if (facility == null) {
            facility = ((Number) rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                    "facilityCode", "ANS27-TC1", "facilityType", "TC", "name", "ANS27-TC1"), admin()), Map.class)
                    .getBody().get("id")).longValue();
        }
        return facility;
    }

    /** "Yes. 1A, 1B, 1C are counted in the calculation." */
    @Test
    @Order(1)
    void everyQ1StandardCountsForTheMetaAreas() {
        Map<String, Object> board = rest.exchange("/api/v1/facilities/" + facility() + "/qcnc-standards",
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class).getBody();
        Map<String, Object> ro1 = ((List<Map<String, Object>>) board.get("metaAreas")).stream()
                .filter(m -> "RO.1".equals(m.get("code"))).findFirst().orElseThrow();
        // RO.1 = Q1 + Q2: all 10 Q1 standards and the 7 of Q2
        assertThat(((Number) ro1.get("requiredStandards")).intValue()).isEqualTo(17);
    }

    /** RA: "Other" area; Volunteers: no area, even if one was sent. */
    @Test
    @Order(2)
    void administrativeReferenceIsOtherAndVolunteersHaveNoArea() {
        Map<String, Object> payload = Map.of(
                "l_team", List.of(Map.of("role", "RA: Administrative Refence")),
                "m_staff", List.of(
                        Map.of("role", "V: Volunteers / Volunteer Group Operators or Members of User or Family"
                                + " Associations", "disciplinary_area", "A: Other"),
                        Map.of("role", "T: Trainees / Staff on Training Placement and/or Voluntary Internship",
                                "disciplinary_area", "PSI: Psychological-Psychotherapeutic"),
                        Map.of("role", "MA: Recreational Therapist - Mental Health Support Worker, Art Masters,"
                                + " Teachers, Job Placement Tutors")));
        ResponseEntity<Map> saved = rest.exchange("/api/v1/facilities/" + facility() + "/set-analysis-grid",
                HttpMethod.PUT, new HttpEntity<>(Map.of("gridVariant", "GAS", "payload", payload), admin()),
                Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> stored = (Map<String, Object>) saved.getBody().get("payload");
        assertThat(((List<Map<String, Object>>) stored.get("l_team")).get(0).get("disciplinary_area"))
                .isEqualTo("A: Other");
        List<Map<String, Object>> staff = (List<Map<String, Object>>) stored.get("m_staff");
        assertThat(staff.get(0)).doesNotContainKey("disciplinary_area");
        // Trainees choose their own area
        assertThat(staff.get(1).get("disciplinary_area")).isEqualTo("PSI: Psychological-Psychotherapeutic");
        assertThat(staff.get(2).get("disciplinary_area")).isEqualTo("AML: Arts-Crafts-Work");
    }

    @Test
    @Order(3)
    void vivacomOffersEightAndNineAsAnswersThatAreNotScored() {
        List<Map<String, Object>> anchors = (List<Map<String, Object>>) questionnaire("VIVACOM").get("anchors");
        assertThat(anchors).hasSize(8);
        assertThat(anchors.subList(6, 8))
                .extracting(a -> ((Number) a.get("value")).intValue(), a -> a.get("label"), a -> a.get("countsInScore"))
                .containsExactly(tuple(8, "Disagree", false), tuple(9, "Not pertinent", false));
        assertThat(anchors.subList(0, 6)).allMatch(a -> Boolean.TRUE.equals(a.get("countsInScore")));
    }

    /** The mechanism on a questionnaire that has items: a non-scoring answer is saved and skipped by scoring. */
    @Test
    @Order(4)
    void aNonScoringAnswerIsSavedButLeftOutOfTheMean() {
        Map<String, Object> dtcro = questionnaire("DTCRO");
        Number dtcroId = (Number) dtcro.get("id");
        jdbc.update("INSERT INTO questionnaire_scale_anchors (questionnaire_id, value, label, counts_in_score)"
                + " VALUES (?, 9, 'Not pertinent', false)", dtcroId.longValue());

        Number formId = (Number) ((List<Map<String, Object>>) dtcro.get("forms")).stream()
                .filter(f -> "STAFF".equals(f.get("formCode"))).findFirst().orElseThrow().get("id");
        List<Map<String, Object>> items = ((List<Map<String, Object>>) dtcro.get("items")).stream()
                .filter(i -> formId.equals(i.get("formId")) && "LIKERT".equals(i.get("responseType"))).toList();
        Object areaId = items.get(0).get("areaId");
        List<Map<String, Object>> sameArea = items.stream().filter(i -> areaId.equals(i.get("areaId"))).toList();

        Number responseId = (Number) rest.exchange("/api/v1/responses", HttpMethod.POST, new HttpEntity<>(Map.of(
                "questionnaireId", dtcroId, "formId", formId, "facilityId", facility(),
                "subjectCode", "ANS27-S1"), admin()), Map.class).getBody().get("id");
        String itemsUrl = "/api/v1/responses/" + responseId + "/items";

        ResponseEntity<Map> outOfScale = rest.exchange(itemsUrl, HttpMethod.PUT, new HttpEntity<>(Map.of("items",
                List.of(Map.of("itemId", sameArea.get(0).get("id"), "numericValue", 7))), admin()), Map.class);
        assertThat(outOfScale.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(outOfScale.getBody().get("message"))).contains("or one of [9]");

        ResponseEntity<Map> saved = rest.exchange(itemsUrl, HttpMethod.PUT, new HttpEntity<>(Map.of("items", List.of(
                Map.of("itemId", sameArea.get(0).get("id"), "numericValue", 9),
                Map.of("itemId", sameArea.get(1).get("id"), "numericValue", 2))), admin()), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT numeric_value FROM questionnaire_response_items"
                + " WHERE response_id = ? AND item_id = ?", Integer.class, responseId.longValue(),
                ((Number) sameArea.get(0).get("id")).longValue())).isEqualTo(9);

        List<ResponseAreaScore> scores = scoringService.computeScores(responseId.longValue(), dtcroId.longValue());
        ResponseAreaScore area = scores.stream()
                .filter(s -> s.getAreaId().equals(((Number) areaId).longValue())).findFirst().orElseThrow();
        assertThat(area.getMeanValue()).isEqualByComparingTo(new BigDecimal("2"));
    }
}
