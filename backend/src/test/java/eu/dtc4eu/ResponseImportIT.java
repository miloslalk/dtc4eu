package eu.dtc4eu;

import eu.dtc4eu.security.dto.AuthDtos.TokenResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec COL-06: paper questionnaires are entered through an XLSX template per
 * form. The upload is validated row by row and committed all-or-nothing;
 * complete rows are submitted (and scored), incomplete ones stay drafts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResponseImportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TestRestTemplate rest;

    private static Long facilityId;
    private static Long dtcroId;
    private static Long userFormId;
    private static List<String> header;

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

    private String importUrl(boolean commit) {
        return "/api/v1/responses/import?questionnaireId=" + dtcroId + "&formId=" + userFormId
                + "&facilityId=" + facilityId + "&commit=" + commit;
    }

    /** A workbook in the template's layout: the given rows under the template header. */
    private static byte[] workbook(List<Map<String, Object>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Responses");
            Row head = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                head.createCell(i).setCellValue(header.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < header.size(); c++) {
                    Object value = rows.get(r).get(header.get(c));
                    if (value instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private ResponseEntity<Map> upload(byte[] xlsx, boolean commit, HttpHeaders headers) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(xlsx) {
            @Override
            public String getFilename() {
                return "paper.xlsx";
            }
        });
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(importUrl(commit), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    /** A row answering every USER item with 3, with the required header fields. */
    private static Map<String, Object> completeRow(String subjectCode) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("subject_code", subjectCode);
        row.put("profile.age", 41);
        row.put("profile.sex", "F");
        header.stream().filter(c -> c.startsWith("USER-")).forEach(c -> row.put(c, 3));
        return row;
    }

    @Test
    @Order(1)
    @SuppressWarnings("unchecked")
    void setUpFacilityRosterAndForm() {
        ResponseEntity<Map> facility = rest.exchange("/api/v1/facilities", HttpMethod.POST, new HttpEntity<>(Map.of(
                "facilityCode", "IMP-TC1", "facilityType", "TC", "name", "Import Test TC"), admin()), Map.class);
        facilityId = ((Number) facility.getBody().get("id")).longValue();
        for (String code : List.of("U-01", "U-02", "U-03")) {
            rest.exchange("/api/v1/facilities/" + facilityId + "/members", HttpMethod.POST,
                    new HttpEntity<>(Map.of("memberCode", code, "category", "USER"), admin()), Map.class);
        }
        ResponseEntity<List> questionnaires = rest.exchange("/api/v1/questionnaires", HttpMethod.GET,
                new HttpEntity<>(admin()), List.class);
        dtcroId = ((List<Map<String, Object>>) questionnaires.getBody()).stream()
                .filter(q -> "DTCRO".equals(q.get("code"))).map(q -> ((Number) q.get("id")).longValue())
                .findFirst().orElseThrow();
        ResponseEntity<Map> detail = rest.exchange("/api/v1/questionnaires/" + dtcroId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        userFormId = ((List<Map<String, Object>>) detail.getBody().get("forms")).stream()
                .filter(f -> "USER".equals(f.get("formCode"))).map(f -> ((Number) f.get("id")).longValue())
                .findFirst().orElseThrow();
    }

    @Test
    @Order(2)
    void templateHasTheSubjectProfileAndItemColumns() throws IOException {
        ResponseEntity<byte[]> template = rest.exchange("/api/v1/responses/import/template?questionnaireId="
                + dtcroId + "&formId=" + userFormId, HttpMethod.GET, new HttpEntity<>(admin()), byte[].class);
        assertThat(template.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(template.getHeaders().getContentDisposition().getFilename()).isEqualTo("DTCRO-USER-template.xlsx");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template.getBody()))) {
            Row head = workbook.getSheet("Responses").getRow(0);
            header = new ArrayList<>();
            head.forEach(cell -> header.add(cell.getStringCellValue()));
            assertThat(workbook.getSheet("Codebook")).isNotNull();
        }
        assertThat(header.get(0)).isEqualTo("subject_code");
        assertThat(header).contains("profile.age", "profile.sex", "profile.work_activity.sheltered", "USER-1.a");
        assertThat(header.stream().filter(c -> c.startsWith("USER-"))).hasSize(36);
    }

    @Test
    @Order(3)
    @SuppressWarnings("unchecked")
    void anInvalidFileIsReportedRowByRowAndNeverImported() throws IOException {
        Map<String, Object> outOfRange = completeRow("U-01");
        outOfRange.put("USER-1.a", 7);
        Map<String, Object> badSex = completeRow("U-02");
        badSex.put("profile.sex", "X");
        byte[] xlsx = workbook(List.of(outOfRange, badSex, completeRow("U-02"), completeRow("U-99")));

        ResponseEntity<Map> report = upload(xlsx, true, admin());
        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(report.getBody().get("committed")).isEqualTo(false);
        assertThat(report.getBody().get("invalidRows")).isEqualTo(4);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.getBody().get("rows");
        assertThat((List<String>) rows.get(0).get("errors")).anyMatch(e -> e.contains("USER-1.a") && e.contains("7"));
        assertThat((List<String>) rows.get(1).get("errors")).anyMatch(e -> e.contains("profile.sex"));
        assertThat((List<String>) rows.get(2).get("errors")).anyMatch(e -> e.contains("repeats row 3"));
        assertThat((List<String>) rows.get(3).get("errors")).anyMatch(e -> e.contains("member roster"));

        ResponseEntity<Map> listed = rest.exchange("/api/v1/responses?facilityId=" + facilityId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        assertThat((List<?>) listed.getBody().get("content")).isEmpty();
    }

    @Test
    @Order(4)
    @SuppressWarnings("unchecked")
    void aValidFileIsPreviewedThenImportedAsSubmittedAndDraftResponses() throws IOException {
        Map<String, Object> partial = completeRow("U-02");
        partial.remove("USER-1.a");
        partial.remove("USER-9.b");
        byte[] xlsx = workbook(List.of(completeRow("U-01"), partial));

        ResponseEntity<Map> preview = upload(xlsx, false, admin());
        assertThat(preview.getBody().get("committed")).isEqualTo(false);
        assertThat(preview.getBody().get("validRows")).isEqualTo(2);
        List<Map<String, Object>> previewRows = (List<Map<String, Object>>) preview.getBody().get("rows");
        assertThat(previewRows.get(0).get("answered")).isEqualTo(36);
        assertThat((List<String>) previewRows.get(1).get("warnings")).anyMatch(w -> w.contains("2 item(s) blank"));

        ResponseEntity<Map> imported = upload(xlsx, true, admin());
        assertThat(imported.getBody().get("committed")).isEqualTo(true);
        assertThat(imported.getBody().get("submitted")).isEqualTo(1);
        assertThat(imported.getBody().get("drafts")).isEqualTo(1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) imported.getBody().get("rows");
        assertThat(rows).extracting(r -> r.get("outcome")).containsExactly("SUBMITTED", "DRAFT");

        Number submittedId = (Number) rows.get(0).get("responseId");
        ResponseEntity<Map> submitted = rest.exchange("/api/v1/responses/" + submittedId, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        assertThat(submitted.getBody().get("status")).isEqualTo("SUBMITTED");
        assertThat(submitted.getBody().get("subjectCode")).isEqualTo("U-01");
        assertThat((Map<String, Object>) submitted.getBody().get("profileSnapshot")).containsEntry("sex", "F");

        // the same file again: both codes already have a USER response in this cycle
        ResponseEntity<Map> again = upload(xlsx, false, admin());
        assertThat(again.getBody().get("invalidRows")).isEqualTo(2);
    }

    @Test
    @Order(5)
    void respondentsCannotImportAndJunkIsRejected() {
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", "imp-respondent", "fullName", "Import Respondent", "password", "ImpResp0nd!",
                "roles", List.of("RESPONDENT")), admin()), Map.class);
        ResponseEntity<Map> denied = upload(new byte[]{1, 2, 3}, false, headersOf("imp-respondent", "ImpResp0nd!"));
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> junk = upload("not a spreadsheet".getBytes(), false, admin());
        assertThat(junk.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
