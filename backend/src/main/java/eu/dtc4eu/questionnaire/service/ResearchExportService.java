package eu.dtc4eu.questionnaire.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.common.web.CsvWriter;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponseItem;
import eu.dtc4eu.questionnaire.entity.QuestionnaireScaleAnchor;
import eu.dtc4eu.questionnaire.repository.QuestionnaireFormRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireScaleAnchorRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Research export for SPSS and R (spec REP-05, REP-07). One questionnaire,
 * one row per response ("wide"): response metadata, the scalar profile
 * header fields ({@code p_…}) and one column per item. The zip holds the
 * data CSV, a codebook, an SPSS syntax file that reads the CSV with variable
 * and value labels, and an R script doing the same — no proprietary writer
 * library is needed and both tools read the identical data file.
 */
@Service
@RequiredArgsConstructor
public class ResearchExportService {

    private static final int MAX_LABEL = 250;
    private static final int TEXT_WIDTH = 2000;

    private final ResponseService responseService;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireFormRepository formRepository;
    private final QuestionnaireItemRepository itemRepository;
    private final QuestionnaireResponseRepository responseRepository;
    private final QuestionnaireResponseItemRepository responseItemRepository;
    private final QuestionnaireScaleAnchorRepository anchorRepository;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    public record ResearchBundle(String fileName, byte[] content) {
    }

    /** One output column: SPSS-safe name, label, numeric or string, and value labels for Likert items. */
    record Variable(String name, String source, String label, boolean numeric, boolean likert) {
    }

    @Transactional(readOnly = true)
    public ResearchBundle export(Long questionnaireId, Long facilityId,
                                 QuestionnaireResponse.RespondentCategory category,
                                 QuestionnaireResponse.Status status, AppUserDetails principal) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> NotFoundException.of("Questionnaire", questionnaireId));
        List<QuestionnaireResponse> responses = responseService.scopedResponses(facilityId, questionnaireId,
                category, status, principal);

        Map<Long, String> facilityCodes = new HashMap<>();
        for (Object[] row : responseRepository.findAllFacilityCodes()) {
            facilityCodes.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        Map<Long, String> formCodes = new HashMap<>();
        List<QuestionnaireItem> items = new ArrayList<>();
        for (QuestionnaireForm form : formRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId)) {
            formCodes.put(form.getId(), form.getFormCode());
            items.addAll(itemRepository.findByFormIdOrderBySortOrderAscIdAsc(form.getId()));
        }
        if (items.isEmpty()) {
            items.addAll(itemRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId));
        }

        // variables: metadata, then profile fields seen in the data, then items
        Set<String> usedNames = new HashSet<>();
        List<Variable> variables = new ArrayList<>();
        variables.add(variable(usedNames, "response_id", "response_id", "Response ID", true, false));
        variables.add(variable(usedNames, "form", "form_code", "Form", false, false));
        variables.add(variable(usedNames, "facility", "facility_code", "Facility code", false, false));
        variables.add(variable(usedNames, "subject", "subject_code", "Pseudonymous subject code", false, false));
        variables.add(variable(usedNames, "category", "respondent_category", "Respondent category", false, false));
        variables.add(variable(usedNames, "admin_mode", "administration_mode", "Administration mode", false, false));
        variables.add(variable(usedNames, "status", "status", "Response status", false, false));
        variables.add(variable(usedNames, "submitted_at", "submitted_at", "Submitted at (ISO-8601)", false, false));

        List<Map<String, Object>> flatProfiles = responses.stream()
                .map(r -> flatten(r.getProfileSnapshot())).toList();
        Map<String, Boolean> profileNumeric = new LinkedHashMap<>();
        for (Map<String, Object> profile : flatProfiles) {
            profile.forEach((key, value) -> profileNumeric.merge(key, value instanceof Number
                    || value instanceof Boolean, Boolean::logicalAnd));
        }
        Map<String, Variable> profileVariables = new LinkedHashMap<>();
        profileNumeric.forEach((key, numeric) -> {
            Variable v = variable(usedNames, "p_" + key, "profile." + key, "Profile: " + key, numeric, false);
            profileVariables.put(key, v);
            variables.add(v);
        });

        Map<Long, Variable> itemVariables = new LinkedHashMap<>();
        for (QuestionnaireItem item : items) {
            boolean likert = item.getResponseType() == QuestionnaireItem.ResponseType.LIKERT;
            Variable v = variable(usedNames, item.getItemCode(), item.getItemCode(), item.getItemText(), likert, likert);
            itemVariables.put(item.getId(), v);
            variables.add(v);
        }

        // data
        CsvWriter data = new CsvWriter().row(variables.stream().map(Variable::name).toArray());
        for (int i = 0; i < responses.size(); i++) {
            QuestionnaireResponse response = responses.get(i);
            Map<String, Object> cells = new HashMap<>();
            cells.put("response_id", response.getId());
            cells.put("form_code", formCodes.get(response.getFormId()));
            cells.put("facility_code", facilityCodes.get(response.getFacilityId()));
            cells.put("subject_code", response.getSubjectCode());
            cells.put("respondent_category", response.getRespondentCategory());
            cells.put("administration_mode", response.getAdministrationMode());
            cells.put("status", response.getStatus());
            cells.put("submitted_at", response.getSubmittedAt());
            flatProfiles.get(i).forEach((key, value) -> cells.put("profile." + key,
                    value instanceof Boolean b ? (b ? 1 : 0) : value));
            for (QuestionnaireResponseItem answer : responseItemRepository.findByResponseId(response.getId())) {
                Variable v = itemVariables.get(answer.getItemId());
                if (v != null) {
                    cells.put(v.source(), v.numeric() ? answer.getNumericValue() : answer.getTextValue());
                }
            }
            data.row(variables.stream().map(v -> cells.get(v.source())).toArray());
        }

        List<QuestionnaireScaleAnchor> anchors = anchorRepository.findByQuestionnaireIdOrderByValue(questionnaireId);
        String base = questionnaire.getCode().toLowerCase() + "-cycle" + cycleContext.requireId();
        byte[] zip = zip(Map.of(
                "data.csv", data.build(),
                "codebook.csv", codebook(variables, anchors),
                base + ".sps", spss(questionnaire, variables, anchors),
                base + ".R", rScript()));

        auditService.success(principal.getId(), "EXPORT", "questionnaire_response", null, Map.of(
                "format", "spss-r", "questionnaireId", questionnaireId, "responses", responses.size(),
                "cycleId", cycleContext.requireId()));
        return new ResearchBundle(base + "-research.zip", zip);
    }

    /* ---------------------------------- files ---------------------------------- */

    private static String codebook(List<Variable> variables, List<QuestionnaireScaleAnchor> anchors) {
        String values = anchors.stream().map(a -> a.getValue() + "=" + a.getLabel())
                .collect(Collectors.joining("; "));
        CsvWriter csv = new CsvWriter().row("variable", "source", "label", "type", "values");
        for (Variable v : variables) {
            csv.row(v.name(), v.source(), v.label(), v.numeric() ? "numeric" : "string", v.likert() ? values : "");
        }
        return csv.build();
    }

    static String spss(Questionnaire questionnaire, List<Variable> variables, List<QuestionnaireScaleAnchor> anchors) {
        StringBuilder sps = new StringBuilder();
        sps.append("* DTC4EU research export — ").append(questionnaire.getCode()).append(".\n")
                .append("* Run from the folder holding data.csv, or edit /FILE to its full path.\n\n")
                .append("GET DATA /TYPE=TXT\n  /FILE='data.csv'\n  /ENCODING='UTF8'\n  /DELCASE=LINE\n")
                .append("  /DELIMITERS=\",\"\n  /QUALIFIER='\"'\n  /ARRANGEMENT=DELIMITED\n  /FIRSTCASE=2\n")
                .append("  /VARIABLES=\n");
        for (Variable v : variables) {
            sps.append("    ").append(v.name()).append(' ')
                    .append(v.numeric() ? (v.likert() ? "F2.0" : "F12.2") : "A" + TEXT_WIDTH).append('\n');
        }
        sps.append(".\n\nVARIABLE LABELS\n");
        for (int i = 0; i < variables.size(); i++) {
            Variable v = variables.get(i);
            sps.append(i == 0 ? "  " : "  /").append(v.name()).append(' ').append(quote(v.label())).append('\n');
        }
        sps.append(".\n");
        List<String> likert = variables.stream().filter(Variable::likert).map(Variable::name).toList();
        if (!likert.isEmpty() && !anchors.isEmpty()) {
            sps.append("\nVALUE LABELS\n  ").append(String.join(" ", likert)).append('\n');
            for (QuestionnaireScaleAnchor anchor : anchors) {
                sps.append("    ").append(anchor.getValue()).append(' ').append(quote(anchor.getLabel())).append('\n');
            }
            sps.append(".\n");
        }
        sps.append("\nEXECUTE.\n");
        return sps.toString();
    }

    private static String rScript() {
        return """
                # DTC4EU research export: reads data.csv with the labels from codebook.csv.
                # Run with the working directory set to the unzipped folder.
                data <- read.csv("data.csv", stringsAsFactors = FALSE, na.strings = "", encoding = "UTF-8",
                                 check.names = FALSE)
                codebook <- read.csv("codebook.csv", stringsAsFactors = FALSE, na.strings = "", encoding = "UTF-8")
                for (i in seq_len(nrow(codebook))) {
                  v <- codebook$variable[i]
                  if (v %in% names(data)) {
                    attr(data[[v]], "label") <- codebook$label[i]
                    if (!is.na(codebook$values[i])) attr(data[[v]], "values") <- codebook$values[i]
                  }
                }
                """;
    }

    private static byte[] zip(Map<String, String> files) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : files.keySet().stream().sorted().toList()) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(files.get(name).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the research export", e);
        }
    }

    /* --------------------------------- internals -------------------------------- */

    /** Scalar profile values keyed "field" or "group_child"; lists and the schema marker are dropped. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> flatten(Map<String, Object> profile) {
        Map<String, Object> flat = new LinkedHashMap<>();
        if (profile == null) {
            return flat;
        }
        profile.forEach((key, value) -> {
            if (ProfileValidationService.SCHEMA_KEY.equals(key)) {
                return;
            }
            if (value instanceof Map<?, ?> group) {
                ((Map<String, Object>) group).forEach((child, childValue) -> {
                    if (!(childValue instanceof Map) && !(childValue instanceof List)) {
                        flat.put(key + "_" + child, childValue);
                    }
                });
            } else if (!(value instanceof List)) {
                flat.put(key, value);
            }
        });
        return flat;
    }

    /** SPSS variable names: letters, digits and _, starting with a letter, ≤ 64 chars, unique (case-blind). */
    static Variable variable(Set<String> used, String wanted, String source, String label, boolean numeric,
                             boolean likert) {
        String name = wanted.replaceAll("[^A-Za-z0-9_]", "_");
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            name = "v_" + name;
        }
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        String candidate = name;
        for (int n = 2; !used.add(candidate.toUpperCase()); n++) {
            candidate = name + "_" + n;
        }
        String safeLabel = label == null ? "" : label.replaceAll("\\s+", " ").trim();
        return new Variable(candidate, source, safeLabel, numeric, likert);
    }

    private static String quote(String label) {
        String trimmed = label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL) : label;
        return "'" + trimmed.replace("'", "''") + "'";
    }
}
