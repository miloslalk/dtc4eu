package eu.dtc4eu.questionnaire.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dtc4eu.common.error.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Per-form profile validation (v0.2 register §2.4): typed keys, no unknowns, required-only-at-submit. */
class ProfileValidationServiceTest {

    private final ProfileValidationService service = new ProfileValidationService();
    private JsonNode schema;

    @BeforeEach
    void loadSchema() throws Exception {
        schema = new ObjectMapper().readTree("""
                {
                  "form": "TEST", "version": 1,
                  "sections": [{
                    "key": "s1", "label": "Section",
                    "fields": [
                      {"key": "age", "label": "Age", "type": "integer", "min": 0, "max": 120, "required": true},
                      {"key": "sex", "label": "Sex", "type": "enum", "options": ["NB", "M", "F"], "required": true},
                      {"key": "hours", "label": "Hours", "type": "decimal", "min": 0, "max": 168},
                      {"key": "note", "label": "Note", "type": "text"},
                      {"key": "single_room", "label": "Single room", "type": "boolean"},
                      {"key": "key_worker", "label": "Key worker", "type": "group", "fields": [
                        {"key": "exists", "label": "Exists", "type": "boolean"}
                      ]},
                      {"key": "sessions", "label": "Sessions", "type": "group",
                       "repeat": {"mode": "fixedRows", "rows": [{"key": "individual", "label": "Individual"}]},
                       "fields": [{"key": "per_month", "label": "Per month", "type": "integer", "min": 0}]},
                      {"key": "staff_rows", "label": "Staff", "type": "group",
                       "repeat": {"mode": "list"},
                       "fields": [{"key": "role", "label": "Role", "type": "text"}]}
                    ]
                  }]
                }
                """);
    }

    @Test
    void validSnapshotPassesInBothModes() {
        Map<String, Object> profile = Map.of(
                "_schema", "TEST.v1",
                "age", 40,
                "sex", "F",
                "hours", 12.5,
                "single_room", true,
                "key_worker", Map.of("exists", true),
                "sessions", Map.of("individual", Map.of("per_month", 4)),
                "staff_rows", List.of(Map.of("role", "nurse")));
        assertThatCode(() -> service.validate(profile, schema, false)).doesNotThrowAnyException();
        assertThatCode(() -> service.validate(profile, schema, true)).doesNotThrowAnyException();
    }

    @Test
    void unknownKeysAreRejectedEvenWhileDraft() {
        assertThatThrownBy(() -> service.validate(Map.of("shoe_size", 42), schema, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("shoe_size");
    }

    @Test
    void requiredFieldsAreEnforcedOnlyAtSubmit() {
        Map<String, Object> partial = Map.of("age", 30);
        assertThatCode(() -> service.validate(partial, schema, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validate(partial, schema, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sex");
        // a missing snapshot fails strict validation the same way
        assertThatThrownBy(() -> service.validate(null, schema, true))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("age");
    }

    @Test
    void typesRangesAndEnumsAreChecked() {
        assertThatThrownBy(() -> service.validate(Map.of("age", 40.5), schema, false))
                .hasMessageContaining("whole number");
        assertThatThrownBy(() -> service.validate(Map.of("age", 200), schema, false))
                .hasMessageContaining("at most");
        assertThatThrownBy(() -> service.validate(Map.of("sex", "X"), schema, false))
                .hasMessageContaining("sex");
        assertThatThrownBy(() -> service.validate(Map.of("single_room", "yes"), schema, false))
                .hasMessageContaining("true or false");
        assertThatThrownBy(() -> service.validate(Map.of("note", 5), schema, false))
                .hasMessageContaining("text");
    }

    @Test
    void groupShapesAreChecked() {
        assertThatThrownBy(() -> service.validate(Map.of("key_worker", Map.of("unknown", 1)), schema, false))
                .hasMessageContaining("key_worker.unknown");
        assertThatThrownBy(() -> service.validate(Map.of("sessions", Map.of("weekly", Map.of())), schema, false))
                .hasMessageContaining("not a known row");
        assertThatThrownBy(() -> service.validate(Map.of("staff_rows", Map.of("role", "x")), schema, false))
                .hasMessageContaining("must be a list");
        assertThatThrownBy(() -> service.validate(
                Map.of("staff_rows", List.of(Map.of("count", 3))), schema, false))
                .hasMessageContaining("not a known field");
    }
}
