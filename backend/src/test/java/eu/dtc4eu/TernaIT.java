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
 * T3 acceptance (spec §7): a coordinator can build a terna, sees only
 * assigned ternas, and validation enforces the facility/coordinator counts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TernaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static final List<Long> facilityIds = new java.util.ArrayList<>();
    private static Long coordinator1Id;
    private static Long coordinator2Id;

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
    void setUpFacilitiesAndCoordinators() {
        for (int i = 1; i <= 4; i++) {
            ResponseEntity<Map> created = rest.exchange("/api/v1/facilities", HttpMethod.POST,
                    new HttpEntity<>(Map.of("facilityCode", "T3-F" + i, "facilityType", "TC",
                            "name", "Terna Test Facility " + i), admin()), Map.class);
            facilityIds.add(((Number) created.getBody().get("id")).longValue());
        }
        // coord1 builds ternas, so an admin assigns it the facility pool it may build from
        ResponseEntity<Map> c1 = rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "coord1", "fullName", "Coordinator One", "password", "Coord1nator!",
                "roles", List.of("COORDINATOR"), "facilityIds", facilityIds), admin()), Map.class);
        ResponseEntity<Map> c2 = rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "coord2", "fullName", "Coordinator Two", "password", "Coord2nator!",
                "roles", List.of("COORDINATOR")), admin()), Map.class);
        coordinator1Id = ((Number) c1.getBody().get("id")).longValue();
        coordinator2Id = ((Number) c2.getBody().get("id")).longValue();
        assertThat(coordinator1Id).isNotNull();
    }

    @Test
    @Order(2)
    void facilityAndCoordinatorCountRulesAreEnforced() {
        HttpHeaders coordinator = headersOf("coord1", "Coord1nator!");

        // 2 facilities → 400
        Map<String, Object> twoFacilities = payload("T1", "TCS", facilityIds.subList(0, 2),
                List.of(coordinator1Id, coordinator2Id));
        assertThat(rest.exchange("/api/v1/ternas", HttpMethod.POST, new HttpEntity<>(twoFacilities, coordinator),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // TCS with 1 coordinator → 400
        Map<String, Object> oneCoordinator = payload("T1", "TCS", facilityIds.subList(0, 3),
                List.of(coordinator1Id));
        ResponseEntity<Map> tcsOne = rest.exchange("/api/v1/ternas", HttpMethod.POST,
                new HttpEntity<>(oneCoordinator, coordinator), Map.class);
        assertThat(tcsOne.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) tcsOne.getBody().get("message")).contains("2 coordinators");
    }

    @Test
    @Order(3)
    void coordinatorBuildsATernaWithFiveDates() {
        HttpHeaders coordinator = headersOf("coord1", "Coord1nator!");

        Map<String, Object> body = payload("T1", "TCS", facilityIds.subList(0, 3),
                List.of(coordinator1Id, coordinator2Id));
        body.put("foundingMeetingDate", "2026-10-01");
        body.put("finalForumDate", "2027-06-15");
        body.put("visitDates", List.of(
                Map.of("visitOrder", 1, "visitDate", "2026-11-15"),
                Map.of("visitOrder", 2, "visitDate", "2027-01-20"),
                Map.of("visitOrder", 3, "visitDate", "2027-03-25")));

        ResponseEntity<Map> created = rest.exchange("/api/v1/ternas", HttpMethod.POST,
                new HttpEntity<>(body, coordinator), Map.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<?>) created.getBody().get("facilities")).hasSize(3);
        assertThat((List<?>) created.getBody().get("coordinators")).hasSize(2);
        assertThat((List<?>) created.getBody().get("visitDates")).hasSize(3);
    }

    @Test
    @Order(4)
    void facilityCannotJoinTwoTernasInOneCycle() {
        HttpHeaders coordinator = headersOf("coord1", "Coord1nator!");

        // T2 reuses facility[2] — already in T1
        Map<String, Object> clash = payload("T2", "GAS",
                List.of(facilityIds.get(1), facilityIds.get(2), facilityIds.get(3)), List.of(coordinator1Id));
        ResponseEntity<Map> response = rest.exchange("/api/v1/ternas", HttpMethod.POST,
                new HttpEntity<>(clash, coordinator), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("message")).contains("another terna in this cycle");
    }

    @Test
    @Order(5)
    void coordinatorSeesOnlyAssignedTernas() {
        // coord2 was assigned to T1 → sees it; a fresh coordinator sees nothing
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "coord3", "fullName", "Coordinator Three", "password", "Coord3nator!",
                "roles", List.of("COORDINATOR")), admin()), Map.class);

        ResponseEntity<Map> forCoord2 = rest.exchange("/api/v1/ternas", HttpMethod.GET,
                new HttpEntity<>(headersOf("coord2", "Coord2nator!")), Map.class);
        assertThat((List<?>) forCoord2.getBody().get("content")).hasSize(1);

        ResponseEntity<Map> forCoord3 = rest.exchange("/api/v1/ternas", HttpMethod.GET,
                new HttpEntity<>(headersOf("coord3", "Coord3nator!")), Map.class);
        assertThat((List<?>) forCoord3.getBody().get("content")).isEmpty();

        // detail access for out-of-scope coordinator → 404
        Number ternaId = (Number) ((List<Map<String, Object>>) forCoord2.getBody().get("content")).get(0).get("id");
        ResponseEntity<Map> detail = rest.exchange("/api/v1/ternas/" + ternaId, HttpMethod.GET,
                new HttpEntity<>(headersOf("coord3", "Coord3nator!")), Map.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(6)
    void internalFacilitatorIsAutoPulledFromParticipation() {
        // facility[3]: set participation with a facilitator, then build a GAS terna around it
        ResponseEntity<Map> facilitator = rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "facil-t3", "fullName", "Facilitator T3", "password", "FacilT3pass!",
                "roles", List.of("INTERNAL_FACILITATOR"), "facilityIds", List.of(facilityIds.get(3))),
                admin()), Map.class);
        Number facilitatorId = (Number) facilitator.getBody().get("id");

        rest.exchange("/api/v1/facilities/" + facilityIds.get(3) + "/participation", HttpMethod.POST,
                new HttpEntity<>(Map.of("activity", "VISITING_TERNA", "internalFacilitatorId", facilitatorId),
                        admin()), Map.class);

        // two more facilities for the terna
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(facilityIds.get(3));
        for (int i = 5; i <= 6; i++) {
            ResponseEntity<Map> created = rest.exchange("/api/v1/facilities", HttpMethod.POST,
                    new HttpEntity<>(Map.of("facilityCode", "T3-F" + i, "facilityType", "GA",
                            "name", "Terna Test Facility " + i), admin()), Map.class);
            ids.add(((Number) created.getBody().get("id")).longValue());
        }

        ResponseEntity<Map> terna = rest.exchange("/api/v1/ternas", HttpMethod.POST,
                new HttpEntity<>(payload("T3", "GAS", ids, List.of(coordinator1Id)), admin()), Map.class);

        assertThat(terna.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<Map<String, Object>> facilities = (List<Map<String, Object>>) terna.getBody().get("facilities");
        Map<String, Object> withFacilitator = facilities.stream()
                .filter(f -> ((Number) f.get("facilityId")).longValue() == facilityIds.get(3)).findFirst()
                .orElseThrow();
        assertThat(((Number) withFacilitator.get("internalFacilitatorId")).longValue())
                .isEqualTo(facilitatorId.longValue());
        assertThat(withFacilitator.get("internalFacilitatorName")).isEqualTo("Facilitator T3");
    }

    /**
     * Security audit: listing facilities on a terna auto-grants the listed
     * coordinators scope over them, so a coordinator may only build from the
     * facility pool an admin assigned them — otherwise it could grant itself
     * access to any facility's data.
     */
    @Test
    @Order(7)
    void coordinatorCannotBuildATernaFromFacilitiesOutsideItsPool() {
        List<Long> unassigned = createFacilities("T3-F7", "T3-F8", "T3-F9");
        ResponseEntity<Map> created = rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "coord-nopool", "fullName", "Coordinator Without Pool", "password", "CoordNoPool1!",
                "roles", List.of("COORDINATOR")), admin()), Map.class);
        assertThat(created.getStatusCode()).as(String.valueOf(created.getBody())).isEqualTo(HttpStatus.CREATED);
        Long noPoolCoordinatorId = ((Number) created.getBody().get("id")).longValue();
        HttpHeaders coordinator = headersOf("coord-nopool", "CoordNoPool1!");

        ResponseEntity<Map> grab = rest.exchange("/api/v1/ternas", HttpMethod.POST,
                new HttpEntity<>(payload("T4", "GAS", unassigned, List.of(noPoolCoordinatorId)), coordinator), Map.class);
        assertThat(grab.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // and the attempt granted nothing
        ResponseEntity<Map> read = rest.exchange("/api/v1/facilities/" + unassigned.get(0), HttpMethod.GET,
                new HttpEntity<>(coordinator), Map.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(8)
    void coordinatorMustCoordinateTheTernaItBuilds() {
        List<Long> pool = createFacilities("T3-F10", "T3-F11", "T3-F12");
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "coord-pool", "fullName", "Coordinator With Pool", "password", "CoordPool1!",
                "roles", List.of("COORDINATOR"), "facilityIds", pool), admin()), Map.class);

        ResponseEntity<Map> forSomeoneElse = rest.exchange("/api/v1/ternas", HttpMethod.POST,
                new HttpEntity<>(payload("T5", "GAS", pool, List.of(coordinator2Id)),
                        headersOf("coord-pool", "CoordPool1!")), Map.class);
        assertThat(forSomeoneElse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) forSomeoneElse.getBody().get("message")).contains("coordinator");
    }

    @Test
    @Order(9)
    @SuppressWarnings("unchecked")
    void coordinatorCannotSwapAnUnassignedFacilityIntoItsOwnTerna() {
        HttpHeaders coordinator = headersOf("coord1", "Coord1nator!");
        ResponseEntity<Map> ternas = rest.exchange("/api/v1/ternas", HttpMethod.GET,
                new HttpEntity<>(coordinator), Map.class);
        Map<String, Object> t1 = ((List<Map<String, Object>>) ternas.getBody().get("content")).stream()
                .filter(t -> "T1".equals(t.get("ternaCode"))).findFirst().orElseThrow();
        Long outsidePool = createFacilities("T3-F13").get(0);

        ResponseEntity<Map> swapped = rest.exchange("/api/v1/ternas/" + t1.get("id"), HttpMethod.PUT,
                new HttpEntity<>(payload("T1", "TCS", List.of(facilityIds.get(0), facilityIds.get(1), outsidePool),
                        List.of(coordinator1Id, coordinator2Id)), coordinator), Map.class);
        assertThat(swapped.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private List<Long> createFacilities(String... codes) {
        List<Long> ids = new java.util.ArrayList<>();
        for (String code : codes) {
            ResponseEntity<Map> created = rest.exchange("/api/v1/facilities", HttpMethod.POST,
                    new HttpEntity<>(Map.of("facilityCode", code, "facilityType", "GA",
                            "name", "Terna Test Facility " + code), admin()), Map.class);
            ids.add(((Number) created.getBody().get("id")).longValue());
        }
        return ids;
    }

    private Map<String, Object> payload(String code, String type, List<Long> facilities, List<Long> coordinators) {
        Map<String, Object> body = new HashMap<>();
        body.put("ternaCode", code);
        body.put("ternaType", type);
        body.put("facilityIds", facilities);
        body.put("coordinatorIds", coordinators);
        return body;
    }
}
