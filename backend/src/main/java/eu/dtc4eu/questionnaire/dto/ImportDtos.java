package eu.dtc4eu.questionnaire.dto;

import java.util.List;

/** Paper-questionnaire XLSX import (spec COL-06): validation report, then all-or-nothing commit. */
public final class ImportDtos {

    private ImportDtos() {
    }

    public enum RowOutcome { VALID, INVALID, SUBMITTED, DRAFT }

    /**
     * One spreadsheet row. {@code answered}/{@code expected} count Likert items;
     * a row that is valid but incomplete is imported as a draft (warning), a
     * complete one is submitted and scored.
     */
    public record ImportRowDto(int row, String subjectCode, int answered, int expected,
                               List<String> errors, List<String> warnings, RowOutcome outcome, Long responseId) {
    }

    public record ImportReport(String facilityCode, String formCode, int totalRows, int validRows, int invalidRows,
                               boolean committed, int submitted, int drafts,
                               List<String> fileErrors, List<ImportRowDto> rows) {
    }
}
