package eu.dtc4eu.facility.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema-driven field derivation for the GAS-Set payload. Kept in the facility
 * package rather than in questionnaire's ProfileValidationService so the GAS
 * save path adds no call into the frozen facility/questionnaire cycle (E5).
 */
final class ProfileDerivations {

    private ProfileDerivations() {
    }

    /**
     * Fills fields the schema derives from another field of the same row when
     * they are still empty, e.g. the GAS-Set L/M disciplinary area from the
     * professional role (Tables 1-2: "simply choosing the role, the
     * corresponding disciplinary area is automatically assigned"). A value
     * already chosen is kept, except for a source listed under "none", which
     * clears it. Returns a copy; the input is not modified.
     */
    static Map<String, Object> apply(Map<String, Object> profile, JsonNode schema) {
        if (profile == null || schema == null) {
            return profile;
        }
        List<JsonNode> fields = new ArrayList<>();
        for (JsonNode section : schema.path("sections")) {
            section.path("fields").forEach(fields::add);
        }
        return derive(profile, fields);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> derive(Map<String, Object> data, List<JsonNode> fields) {
        Map<String, Object> copy = new LinkedHashMap<>(data);
        for (JsonNode field : fields) {
            String key = field.path("key").asText();
            Object value = copy.get(key);
            if ("group".equals(field.path("type").asText()) && value != null) {
                List<JsonNode> subFields = new ArrayList<>();
                field.path("fields").forEach(subFields::add);
                if (value instanceof List<?> rows) {
                    copy.put(key, rows.stream()
                            .map(row -> row instanceof Map<?, ?> m ? derive((Map<String, Object>) m, subFields) : row)
                            .toList());
                } else if (value instanceof Map<?, ?> map && field.path("repeat").path("mode").asText()
                        .equals("fixedRows")) {
                    Map<String, Object> byRow = new LinkedHashMap<>();
                    map.forEach((rowKey, row) -> byRow.put(String.valueOf(rowKey),
                            row instanceof Map<?, ?> m ? derive((Map<String, Object>) m, subFields) : row));
                    copy.put(key, byRow);
                } else if (value instanceof Map<?, ?> map) {
                    copy.put(key, derive((Map<String, Object>) map, subFields));
                }
            }
            JsonNode derivedFrom = field.path("derivedFrom");
            if (derivedFrom.isMissingNode()) {
                continue;
            }
            Object source = copy.get(derivedFrom.path("field").asText());
            boolean empty = value == null || (value instanceof String text && text.isBlank());
            if (source instanceof String sourceText && hasNone(derivedFrom, sourceText)) {
                copy.remove(key);
            } else if (empty && source instanceof String sourceText
                    && derivedFrom.path("map").hasNonNull(sourceText)) {
                copy.put(key, derivedFrom.path("map").get(sourceText).asText());
            }
        }
        return copy;
    }

    /** Source values listed under "none" have no derived value at all (GAS-Set M: Volunteers have no area). */
    private static boolean hasNone(JsonNode derivedFrom, String sourceText) {
        for (JsonNode none : derivedFrom.path("none")) {
            if (sourceText.equals(none.asText())) {
                return true;
            }
        }
        return false;
    }

}
