package eu.dtc4eu.questionnaire.service;

import com.fasterxml.jackson.databind.JsonNode;
import eu.dtc4eu.common.error.BadRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a profile snapshot against a per-form schema (register §2.4).
 * Unknown keys are always rejected; {@code required} fields are enforced only
 * on submit (strict mode) so drafts can be saved half-filled.
 */
@Service
public class ProfileValidationService {

    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_LIST_ROWS = 200;
    private static final int MAX_ERRORS = 20;
    /** Reserved marker key identifying the schema a snapshot was captured with. */
    public static final String SCHEMA_KEY = "_schema";

    public void validate(Map<String, Object> profile, JsonNode schema, boolean strict) {
        if (schema == null) {
            return;
        }
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = profile != null ? profile : Map.of();
        List<JsonNode> fields = new ArrayList<>();
        for (JsonNode section : schema.path("sections")) {
            section.path("fields").forEach(fields::add);
        }
        validateFields(data, fields, "", strict, errors, Set.of(SCHEMA_KEY));
        if (!errors.isEmpty()) {
            throw new BadRequestException("Profile invalid: " + String.join("; ",
                    errors.subList(0, Math.min(errors.size(), MAX_ERRORS))));
        }
    }

    private void validateFields(Map<String, Object> data, List<JsonNode> fields, String path,
                                boolean strict, List<String> errors, Set<String> extraAllowed) {
        Set<String> allowed = new HashSet<>(extraAllowed);
        for (JsonNode field : fields) {
            allowed.add(field.path("key").asText());
        }
        for (String key : data.keySet()) {
            if (!allowed.contains(key)) {
                errors.add(path + key + " is not a known field");
            }
        }
        for (JsonNode field : fields) {
            String key = field.path("key").asText();
            Object value = data.get(key);
            if (value == null) {
                if (strict && field.path("required").asBoolean(false)) {
                    errors.add(path + key + " is required");
                }
                continue;
            }
            validateValue(value, field, path + key, strict, errors);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateValue(Object value, JsonNode field, String path, boolean strict, List<String> errors) {
        String type = field.path("type").asText();
        switch (type) {
            case "integer" -> {
                BigDecimal number = asNumber(value);
                if (number == null || number.stripTrailingZeros().scale() > 0) {
                    errors.add(path + " must be a whole number");
                } else {
                    checkRange(number, field, path, errors);
                }
            }
            case "decimal" -> {
                BigDecimal number = asNumber(value);
                if (number == null) {
                    errors.add(path + " must be a number");
                } else {
                    checkRange(number, field, path, errors);
                }
            }
            case "text" -> {
                if (!(value instanceof String s)) {
                    errors.add(path + " must be text");
                } else if (s.length() > MAX_TEXT_LENGTH) {
                    errors.add(path + " is too long");
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    errors.add(path + " must be true or false");
                }
            }
            case "enum" -> {
                Set<String> options = new HashSet<>();
                field.path("options").forEach(o -> options.add(o.asText()));
                if (!(value instanceof String s) || !options.contains(s)) {
                    errors.add(path + " must be one of " + options);
                }
            }
            case "group" -> validateGroup(value, field, path, strict, errors);
            default -> errors.add(path + " has an unsupported schema type '" + type + "'");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateGroup(Object value, JsonNode field, String path, boolean strict, List<String> errors) {
        List<JsonNode> subFields = new ArrayList<>();
        field.path("fields").forEach(subFields::add);
        JsonNode repeat = field.path("repeat");

        if (repeat.isMissingNode()) {
            if (!(value instanceof Map<?, ?> map)) {
                errors.add(path + " must be an object");
                return;
            }
            validateFields((Map<String, Object>) map, subFields, path + ".", strict, errors, Set.of());
            return;
        }

        String mode = repeat.path("mode").asText();
        if ("fixedRows".equals(mode)) {
            if (!(value instanceof Map<?, ?> map)) {
                errors.add(path + " must be an object keyed by row");
                return;
            }
            Set<String> rowKeys = new HashSet<>();
            repeat.path("rows").forEach(r -> rowKeys.add(r.path("key").asText()));
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String rowKey = String.valueOf(entry.getKey());
                if (!rowKeys.contains(rowKey)) {
                    errors.add(path + "." + rowKey + " is not a known row");
                    continue;
                }
                if (!(entry.getValue() instanceof Map<?, ?> row)) {
                    errors.add(path + "." + rowKey + " must be an object");
                    continue;
                }
                validateFields((Map<String, Object>) row, subFields, path + "." + rowKey + ".", strict, errors,
                        Set.of());
            }
        } else if ("list".equals(mode)) {
            if (!(value instanceof List<?> list)) {
                errors.add(path + " must be a list");
                return;
            }
            if (list.size() > MAX_LIST_ROWS) {
                errors.add(path + " has too many rows");
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?, ?> row)) {
                    errors.add(path + "[" + i + "] must be an object");
                    continue;
                }
                validateFields((Map<String, Object>) row, subFields, path + "[" + i + "].", strict, errors,
                        Set.of());
            }
        } else {
            errors.add(path + " has an unsupported repeat mode '" + mode + "'");
        }
    }

    private BigDecimal asNumber(Object value) {
        if (value instanceof Boolean || !(value instanceof Number number)) {
            return null;
        }
        return new BigDecimal(number.toString());
    }

    private void checkRange(BigDecimal number, JsonNode field, String path, List<String> errors) {
        if (field.hasNonNull("min") && number.compareTo(BigDecimal.valueOf(field.get("min").asDouble())) < 0) {
            errors.add(path + " must be at least " + field.get("min").asText());
        }
        if (field.hasNonNull("max") && number.compareTo(BigDecimal.valueOf(field.get("max").asDouble())) > 0) {
            errors.add(path + " must be at most " + field.get("max").asText());
        }
    }
}
