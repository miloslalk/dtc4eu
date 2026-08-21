package eu.dtc4eu.website.page.service;

import eu.dtc4eu.website.common.error.BadRequestException;
import eu.dtc4eu.website.common.web.HtmlSanitizerService;
import eu.dtc4eu.website.page.entity.BlockType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a block payload against its BlockRegistry spec and returns the
 * normalized payload to store: unknown keys are rejected, blank optional
 * values are dropped, and every HTML field passes through the sanitizer.
 */
@Service
@RequiredArgsConstructor
public class BlockValidationService {

    private final HtmlSanitizerService htmlSanitizer;

    public Map<String, Object> validate(BlockType type, Map<String, Object> payload) {
        List<BlockRegistry.Field> spec = BlockRegistry.SPECS.get(type);
        List<String> errors = new ArrayList<>();
        Map<String, Object> normalized = validateObject(type + "", spec, payload != null ? payload : Map.of(), errors);
        if (!errors.isEmpty()) {
            throw new BadRequestException("Invalid " + type + " block: " + String.join("; ", errors));
        }
        return normalized;
    }

    private Map<String, Object> validateObject(String path, List<BlockRegistry.Field> spec,
                                               Map<String, Object> payload, List<String> errors) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String key : payload.keySet()) {
            if (spec.stream().noneMatch(f -> f.key().equals(key))) {
                errors.add(path + " has unknown field '" + key + "'");
            }
        }
        for (BlockRegistry.Field field : spec) {
            Object raw = payload.get(field.key());
            String fieldPath = path + "." + field.key();
            Object value = validateField(field, fieldPath, raw, errors);
            if (value == null) {
                if (field.required()) {
                    errors.add(fieldPath + " is required");
                }
            } else {
                normalized.put(field.key(), value);
            }
        }
        return normalized;
    }

    /** Returns the normalized value, or null when absent/blank (caller enforces required). */
    @SuppressWarnings("unchecked")
    private Object validateField(BlockRegistry.Field field, String path, Object raw, List<String> errors) {
        if (raw == null) {
            return null;
        }
        switch (field.kind()) {
            case STRING -> {
                if (!(raw instanceof String s)) {
                    errors.add(path + " must be a string");
                    return null;
                }
                String trimmed = s.trim();
                if (trimmed.isEmpty()) {
                    return null;
                }
                if (trimmed.length() > field.maxLength()) {
                    errors.add(path + " exceeds " + field.maxLength() + " characters");
                    return null;
                }
                return trimmed;
            }
            case HTML -> {
                if (!(raw instanceof String s)) {
                    errors.add(path + " must be a string");
                    return null;
                }
                if (s.length() > field.maxLength()) {
                    errors.add(path + " is too long");
                    return null;
                }
                String clean = htmlSanitizer.sanitize(s);
                return clean.isBlank() ? null : clean;
            }
            case INTEGER, MEDIA_ID, REFERENCE_ID -> {
                if (raw instanceof Number n) {
                    return n.longValue();
                }
                errors.add(path + " must be a number");
                return null;
            }
            case BOOLEAN -> {
                if (raw instanceof Boolean b) {
                    return b;
                }
                errors.add(path + " must be a boolean");
                return null;
            }
            case ENUM -> {
                if (raw instanceof String s && field.options().contains(s)) {
                    return s;
                }
                errors.add(path + " must be one of " + field.options());
                return null;
            }
            case ITEMS -> {
                if (!(raw instanceof List<?> list)) {
                    errors.add(path + " must be a list");
                    return null;
                }
                List<Map<String, Object>> items = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    Object entry = list.get(i);
                    if (!(entry instanceof Map)) {
                        errors.add(path + "[" + i + "] must be an object");
                        continue;
                    }
                    items.add(validateObject(path + "[" + i + "]", field.itemFields(),
                            (Map<String, Object>) entry, errors));
                }
                return items.isEmpty() ? null : items;
            }
            default -> {
                return null;
            }
        }
    }
}
