package eu.dtc4eu.questionnaire.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the per-form profile-header definitions (v0.2 register §2.4) from
 * classpath resources profile-schemas/{questionnaire}/{FORM}.json. Forms
 * without a resource simply have no profile step.
 */
@Service
@RequiredArgsConstructor
public class ProfileSchemaService {

    private final ObjectMapper objectMapper;
    private final Map<String, Optional<JsonNode>> cache = new ConcurrentHashMap<>();

    public Optional<JsonNode> find(String questionnaireCode, String formCode) {
        if (questionnaireCode == null || formCode == null
                || !questionnaireCode.matches("[A-Za-z0-9_-]+") || !formCode.matches("[A-Za-z0-9_-]+")) {
            return Optional.empty();
        }
        String path = "profile-schemas/" + questionnaireCode.toLowerCase(Locale.ROOT) + "/" + formCode + ".json";
        return cache.computeIfAbsent(path, this::load);
    }

    private Optional<JsonNode> load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(objectMapper.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read profile schema " + path, e);
        }
    }
}
