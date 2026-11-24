package eu.dtc4eu.questionnaire.service;

import com.fasterxml.jackson.databind.JsonNode;
import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.questionnaire.dto.ImportDtos.ImportReport;
import eu.dtc4eu.questionnaire.dto.ImportDtos.ImportRowDto;
import eu.dtc4eu.questionnaire.dto.ImportDtos.RowOutcome;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.CreateResponseRequest;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ItemAnswer;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.ResponseDetailDto;
import eu.dtc4eu.questionnaire.dto.ResponseDtos.SaveItemsRequest;
import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireForm;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import eu.dtc4eu.questionnaire.repository.QuestionnaireFormRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Paper questionnaires → platform (spec COL-06, A3). A coordinator downloads
 * an XLSX template for one form (one row per respondent: subject code, the
 * form's profile header fields, one column per item), fills it from the paper
 * forms and uploads it. The upload is first validated row by row — unknown or
 * duplicate subject codes, out-of-range values, malformed header answers —
 * and only an error-free file can be committed. Commit goes through
 * {@link ResponseService}, so scope checks, audit logging and the scoring
 * engine behave exactly as for online entry: complete rows are submitted,
 * incomplete ones stay drafts.
 */
@Service
@RequiredArgsConstructor
public class ResponseImportService {

    static final String SUBJECT_COLUMN = "subject_code";
    static final String PROFILE_PREFIX = "profile.";
    private static final String DATA_SHEET = "Responses";
    private static final int MAX_ROWS = 1000;

    private final ResponseService responseService;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireFormRepository formRepository;
    private final QuestionnaireItemRepository itemRepository;
    private final QuestionnaireResponseRepository responseRepository;
    private final ProfileSchemaService profileSchemaService;
    private final ProfileValidationService profileValidationService;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    /** A flattened scalar profile field: "work_activity.sheltered" → group key + child key. */
    record ProfileColumn(String path, String label, String type, List<String> options, boolean required) {
    }

    private record Definition(Questionnaire questionnaire, QuestionnaireForm form, List<QuestionnaireItem> items,
                              List<ProfileColumn> profileColumns, JsonNode schema) {
    }

    /* --------------------------------- template --------------------------------- */

    @Transactional(readOnly = true)
    public byte[] template(Long questionnaireId, Long formId) {
        Definition definition = definition(questionnaireId, formId);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);

            Sheet data = workbook.createSheet(DATA_SHEET);
            Row header = data.createRow(0);
            List<String> columns = columns(definition);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(bold);
                data.setColumnWidth(i, Math.max(12, columns.get(i).length() + 2) * 256);
            }
            data.createFreezePane(1, 1);
            addPickLists(data, definition, columns);

            Sheet codebook = workbook.createSheet("Codebook");
            Row codebookHeader = codebook.createRow(0);
            String[] titles = {"column", "meaning", "allowed values", "needed to submit"};
            for (int i = 0; i < titles.length; i++) {
                codebookHeader.createCell(i).setCellValue(titles[i]);
                codebookHeader.getCell(i).setCellStyle(bold);
            }
            int r = 1;
            r = codebookRow(codebook, r, SUBJECT_COLUMN, "Pseudonymous subject code (never a name)", "text, max 40", true);
            for (ProfileColumn column : definition.profileColumns()) {
                r = codebookRow(codebook, r, PROFILE_PREFIX + column.path(), column.label(), allowed(column),
                        column.required());
            }
            String scale = definition.questionnaire().getScaleMin() + "–" + definition.questionnaire().getScaleMax()
                    + " (blank = not answered)";
            for (QuestionnaireItem item : definition.items()) {
                boolean likert = item.getResponseType() == QuestionnaireItem.ResponseType.LIKERT;
                r = codebookRow(codebook, r, item.getItemCode(), item.getItemText(), likert ? scale : "free text", likert);
            }
            codebook.setColumnWidth(0, 28 * 256);
            codebook.setColumnWidth(1, 90 * 256);
            codebook.setColumnWidth(2, 30 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the XLSX template", e);
        }
    }

    public String templateFileName(Long questionnaireId, Long formId) {
        Definition definition = definition(questionnaireId, formId);
        return definition.questionnaire().getCode() + "-" + definition.form().getFormCode() + "-template.xlsx";
    }

    private static int codebookRow(Sheet sheet, int index, String column, String meaning, String allowed,
                                   boolean required) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(column);
        row.createCell(1).setCellValue(meaning == null ? "" : meaning);
        row.createCell(2).setCellValue(allowed);
        row.createCell(3).setCellValue(required ? "yes" : "");
        return index + 1;
    }

    private void addPickLists(Sheet sheet, Definition definition, List<String> columns) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        String[] likert = java.util.stream.IntStream
                .rangeClosed(definition.questionnaire().getScaleMin(), definition.questionnaire().getScaleMax())
                .mapToObj(String::valueOf).toArray(String[]::new);
        Map<String, ProfileColumn> profileByColumn = definition.profileColumns().stream()
                .collect(Collectors.toMap(c -> PROFILE_PREFIX + c.path(), c -> c));
        Set<String> likertCodes = definition.items().stream()
                .filter(i -> i.getResponseType() == QuestionnaireItem.ResponseType.LIKERT)
                .map(QuestionnaireItem::getItemCode).collect(Collectors.toSet());
        for (int i = 0; i < columns.size(); i++) {
            String[] options = null;
            if (likertCodes.contains(columns.get(i))) {
                options = likert;
            } else if (profileByColumn.containsKey(columns.get(i))) {
                ProfileColumn column = profileByColumn.get(columns.get(i));
                if ("enum".equals(column.type())) {
                    options = column.options().toArray(String[]::new);
                } else if ("boolean".equals(column.type())) {
                    options = new String[]{"yes", "no"};
                }
            }
            if (options != null && options.length > 0) {
                DataValidation validation = helper.createValidation(helper.createExplicitListConstraint(options),
                        new CellRangeAddressList(1, MAX_ROWS, i, i));
                validation.setShowErrorBox(true);
                sheet.addValidationData(validation);
            }
        }
    }

    private static String allowed(ProfileColumn column) {
        return switch (column.type()) {
            case "integer" -> "whole number";
            case "decimal" -> "number";
            case "boolean" -> "yes / no";
            case "date" -> "date YYYY-MM-DD";
            case "enum" -> String.join(" / ", column.options());
            default -> "text";
        };
    }

    /* ---------------------------------- import ---------------------------------- */

    /**
     * Validates the upload and, when {@code commit} is set and every row is
     * valid, imports it in one transaction. A file with any invalid row is
     * never partially imported.
     */
    @Transactional
    public ImportReport importFile(Long questionnaireId, Long formId, Long facilityId, InputStream file,
                                   boolean commit, AppUserDetails actor) {
        Definition definition = definition(questionnaireId, formId);
        Long cycleId = commit ? cycleContext.requireWritable().getId() : cycleContext.requireId();
        if (!dataScopeService.scopeOf(actor).canSeeFacility(facilityId)) {
            throw NotFoundException.of("Facility", facilityId);
        }
        String facilityCode = responseRepository.findFacilityCode(facilityId)
                .orElseThrow(() -> NotFoundException.of("Facility", facilityId));

        List<String> fileErrors = new ArrayList<>();
        List<ParsedRow> parsed = parse(file, definition, fileErrors);

        Set<String> roster = responseRepository.findRosterCodes(facilityId, cycleId).stream()
                .map(code -> code.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> alreadyEntered = responseRepository.findByAnnualCycleIdAndFormIdAndSubjectCodeIn(cycleId,
                        formId, parsed.stream().map(ParsedRow::subjectCode).filter(c -> c != null).toList()).stream()
                .map(r -> r.getSubjectCode().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<String, Integer> firstRowOfCode = new HashMap<>();
        long likertTotal = definition.items().stream()
                .filter(i -> i.getResponseType() == QuestionnaireItem.ResponseType.LIKERT).count();

        List<ImportRowDto> rows = new ArrayList<>();
        List<Boolean> completeRows = new ArrayList<>();
        for (ParsedRow row : parsed) {
            List<String> errors = new ArrayList<>(row.errors());
            List<String> warnings = new ArrayList<>();
            String code = row.subjectCode();
            if (code == null) {
                errors.add("subject_code is empty");
            } else {
                String key = code.toUpperCase(Locale.ROOT);
                Integer first = firstRowOfCode.putIfAbsent(key, row.rowNumber());
                if (first != null) {
                    errors.add("subject_code " + code + " repeats row " + first);
                }
                if (alreadyEntered.contains(key)) {
                    errors.add("subject_code " + code + " already has a " + definition.form().getFormCode()
                            + " response in this cycle");
                }
                responseService.singleCommunityConflict(cycleId, facilityId, code,
                        responseService.categoryOf(definition.form()), null).ifPresent(errors::add);
                if (!roster.isEmpty() && !roster.contains(key)) {
                    errors.add("subject_code " + code + " is not in " + facilityCode + "'s member roster");
                } else if (roster.isEmpty()) {
                    warnings.add("no member roster recorded for " + facilityCode + " — code not checked");
                }
            }
            try {
                profileValidationService.validate(row.profile(), definition.schema(), false);
            } catch (BadRequestException e) {
                errors.add(e.getMessage());
            }
            int answered = (int) row.likertAnswered();
            boolean complete = answered == likertTotal && strictProfileOk(row.profile(), definition.schema());
            if (errors.isEmpty() && !complete) {
                warnings.add(answered < likertTotal
                        ? (likertTotal - answered) + " item(s) blank — imported as a draft"
                        : "profile header incomplete — imported as a draft");
            }
            completeRows.add(complete);
            rows.add(new ImportRowDto(row.rowNumber(), code, answered, (int) likertTotal, errors, warnings,
                    errors.isEmpty() ? RowOutcome.VALID : RowOutcome.INVALID, null));
        }

        int invalid = (int) rows.stream().filter(r -> r.outcome() == RowOutcome.INVALID).count();
        if (parsed.isEmpty() && fileErrors.isEmpty()) {
            fileErrors.add("The sheet has no data rows");
        }
        boolean canCommit = commit && invalid == 0 && fileErrors.isEmpty();
        if (!canCommit) {
            return new ImportReport(facilityCode, definition.form().getFormCode(), rows.size(), rows.size() - invalid,
                    invalid, false, 0, 0, fileErrors, rows);
        }

        List<ImportRowDto> committed = new ArrayList<>();
        int submitted = 0;
        for (int i = 0; i < parsed.size(); i++) {
            ParsedRow row = parsed.get(i);
            ImportRowDto checked = rows.get(i);
            ResponseDetailDto created = responseService.create(new CreateResponseRequest(questionnaireId, formId,
                    facilityId, row.subjectCode(), null, null, row.profile().isEmpty() ? null : row.profile()), actor);
            if (!row.answers().isEmpty()) {
                responseService.saveItems(created.id(), new SaveItemsRequest(row.answers()), actor);
            }
            boolean complete = completeRows.get(i);
            if (complete) {
                responseService.submit(created.id(), actor);
                submitted++;
            }
            committed.add(new ImportRowDto(checked.row(), checked.subjectCode(), checked.answered(),
                    checked.expected(), checked.errors(), checked.warnings(),
                    complete ? RowOutcome.SUBMITTED : RowOutcome.DRAFT, created.id()));
        }
        auditService.success(actor.getId(), "IMPORT", "questionnaire_response", null, Map.of(
                "form", definition.form().getFormCode(), "facilityId", facilityId,
                "rows", committed.size(), "submitted", submitted));
        return new ImportReport(facilityCode, definition.form().getFormCode(), rows.size(), rows.size(), 0, true,
                submitted, committed.size() - submitted, fileErrors, committed);
    }

    private boolean strictProfileOk(Map<String, Object> profile, JsonNode schema) {
        try {
            profileValidationService.validate(profile, schema, true);
            return true;
        } catch (BadRequestException e) {
            return false;
        }
    }

    private record ParsedRow(int rowNumber, String subjectCode, Map<String, Object> profile,
                             List<ItemAnswer> answers, long likertAnswered, List<String> errors) {
    }

    private List<ParsedRow> parse(InputStream file, Definition definition, List<String> fileErrors) {
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(file);
        } catch (IOException | RuntimeException e) {
            throw new BadRequestException("The upload is not a readable XLSX workbook");
        }
        try (workbook) {
            Sheet sheet = workbook.getSheet(DATA_SHEET);
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            Row header = sheet.getRow(0);
            if (header == null) {
                fileErrors.add("The first row must hold the column names of the template");
                return List.of();
            }
            Map<String, QuestionnaireItem> itemsByCode = definition.items().stream()
                    .collect(Collectors.toMap(i -> i.getItemCode().toUpperCase(Locale.ROOT), i -> i));
            Map<String, ProfileColumn> profileByColumn = definition.profileColumns().stream()
                    .collect(Collectors.toMap(c -> (PROFILE_PREFIX + c.path()).toUpperCase(Locale.ROOT), c -> c));

            Map<Integer, String> columnAt = new LinkedHashMap<>();
            Set<String> seenColumns = new HashSet<>();
            for (Cell cell : header) {
                String name = formatter.formatCellValue(cell).trim();
                if (name.isEmpty()) {
                    continue;
                }
                String key = name.toUpperCase(Locale.ROOT);
                if (!seenColumns.add(key)) {
                    fileErrors.add("Column " + name + " appears twice");
                } else if (!key.equals(SUBJECT_COLUMN.toUpperCase(Locale.ROOT)) && !itemsByCode.containsKey(key)
                        && !profileByColumn.containsKey(key)) {
                    fileErrors.add("Column " + name + " is not part of form " + definition.form().getFormCode());
                }
                columnAt.put(cell.getColumnIndex(), key);
            }
            if (!seenColumns.contains(SUBJECT_COLUMN.toUpperCase(Locale.ROOT))) {
                fileErrors.add("Column subject_code is missing");
            }

            int scaleMin = definition.questionnaire().getScaleMin();
            int scaleMax = definition.questionnaire().getScaleMax();
            List<ParsedRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlank(row, formatter)) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    fileErrors.add("Only the first " + MAX_ROWS + " rows are read; split the file");
                    break;
                }
                String subjectCode = null;
                Map<String, Object> profile = new LinkedHashMap<>();
                List<ItemAnswer> answers = new ArrayList<>();
                long likertAnswered = 0;
                List<String> errors = new ArrayList<>();
                for (Map.Entry<Integer, String> column : columnAt.entrySet()) {
                    Cell cell = row.getCell(column.getKey());
                    String text = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    String key = column.getValue();
                    if (key.equals(SUBJECT_COLUMN.toUpperCase(Locale.ROOT))) {
                        if (text.length() > 40) {
                            errors.add("subject_code is longer than 40 characters");
                        }
                        subjectCode = text;
                    } else if (itemsByCode.containsKey(key)) {
                        QuestionnaireItem item = itemsByCode.get(key);
                        if (item.getResponseType() == QuestionnaireItem.ResponseType.LIKERT) {
                            Integer value = wholeNumber(cell, text);
                            if (value == null || value < scaleMin || value > scaleMax) {
                                errors.add(item.getItemCode() + ": \"" + text + "\" is not a value between "
                                        + scaleMin + " and " + scaleMax);
                            } else {
                                answers.add(new ItemAnswer(item.getId(), value, null));
                                likertAnswered++;
                            }
                        } else {
                            answers.add(new ItemAnswer(item.getId(), null, text));
                        }
                    } else if (profileByColumn.containsKey(key)) {
                        ProfileColumn profileColumn = profileByColumn.get(key);
                        Object value = profileValue(profileColumn, cell, text);
                        if (value == null) {
                            errors.add(PROFILE_PREFIX + profileColumn.path() + ": \"" + text + "\" is not "
                                    + allowed(profileColumn));
                        } else {
                            putPath(profile, profileColumn.path(), value);
                        }
                    }
                }
                rows.add(new ParsedRow(r + 1, subjectCode, profile, answers, likertAnswered, errors));
            }
            return rows;
        } catch (IOException e) {
            throw new BadRequestException("The upload could not be read");
        }
    }

    private static boolean isBlank(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static Integer wholeNumber(Cell cell, String text) {
        try {
            BigDecimal number = cell.getCellType() == CellType.NUMERIC
                    ? BigDecimal.valueOf(cell.getNumericCellValue()) : new BigDecimal(text);
            return number.stripTrailingZeros().scale() > 0 ? null : number.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** Converts a cell to the JSON value the profile schema expects, or null when it does not fit. */
    private static Object profileValue(ProfileColumn column, Cell cell, String text) {
        switch (column.type()) {
            case "integer":
                return wholeNumber(cell, text);
            case "decimal":
                try {
                    return cell.getCellType() == CellType.NUMERIC
                            ? BigDecimal.valueOf(cell.getNumericCellValue()) : new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    return null;
                }
            case "boolean":
                String flag = text.toLowerCase(Locale.ROOT);
                if (Set.of("yes", "y", "true", "1").contains(flag)) {
                    return Boolean.TRUE;
                }
                return Set.of("no", "n", "false", "0").contains(flag) ? Boolean.FALSE : null;
            case "enum":
                return column.options().stream().filter(o -> o.equalsIgnoreCase(text)).findFirst().orElse(null);
            default:
                return text;
        }
    }

    @SuppressWarnings("unchecked")
    private static void putPath(Map<String, Object> profile, String path, Object value) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            profile.put(path, value);
            return;
        }
        Map<String, Object> group = (Map<String, Object>) profile.computeIfAbsent(path.substring(0, dot),
                k -> new LinkedHashMap<String, Object>());
        group.put(path.substring(dot + 1), value);
    }

    /* --------------------------------- internals -------------------------------- */

    private Definition definition(Long questionnaireId, Long formId) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> NotFoundException.of("Questionnaire", questionnaireId));
        QuestionnaireForm form = formRepository.findById(formId)
                .filter(f -> f.getQuestionnaireId().equals(questionnaireId))
                .orElseThrow(() -> new BadRequestException("Form does not belong to this questionnaire"));
        List<QuestionnaireItem> items = itemRepository.findByFormIdOrderBySortOrderAscIdAsc(formId);
        JsonNode schema = form.getFormCode() == null ? null
                : profileSchemaService.find(questionnaire.getCode(), form.getFormCode()).orElse(null);
        return new Definition(questionnaire, form, items, profileColumns(schema), schema);
    }

    /**
     * The template's header columns: the schema's scalar fields, and the scalar
     * children of its groups as "group.child". List-valued fields (multiselect,
     * repeating rows) are not spreadsheet-friendly and are completed on screen.
     */
    static List<ProfileColumn> profileColumns(JsonNode schema) {
        List<ProfileColumn> columns = new ArrayList<>();
        if (schema == null) {
            return columns;
        }
        for (JsonNode section : schema.path("sections")) {
            for (JsonNode field : section.path("fields")) {
                String type = field.path("type").asText();
                if ("group".equals(type) && field.path("repeat").isMissingNode()) {
                    for (JsonNode child : field.path("fields")) {
                        addScalar(columns, field.path("key").asText() + "." + child.path("key").asText(),
                                field.path("label").asText() + " — " + child.path("label").asText(), child);
                    }
                } else {
                    addScalar(columns, field.path("key").asText(), field.path("label").asText(), field);
                }
            }
        }
        return columns;
    }

    private static void addScalar(List<ProfileColumn> columns, String path, String label, JsonNode field) {
        String type = field.path("type").asText();
        if (!Set.of("integer", "decimal", "text", "date", "boolean", "enum").contains(type)) {
            return;
        }
        List<String> options = new ArrayList<>();
        field.path("options").forEach(o -> options.add(o.isObject() ? o.path("value").asText() : o.asText()));
        columns.add(new ProfileColumn(path, label, type, options, field.path("required").asBoolean(false)));
    }

    private static List<String> columns(Definition definition) {
        List<String> columns = new ArrayList<>();
        columns.add(SUBJECT_COLUMN);
        definition.profileColumns().forEach(c -> columns.add(PROFILE_PREFIX + c.path()));
        definition.items().forEach(i -> columns.add(i.getItemCode()));
        return columns;
    }
}
