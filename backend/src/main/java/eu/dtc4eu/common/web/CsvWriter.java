package eu.dtc4eu.common.web;

import java.util.List;

/**
 * Minimal RFC-4180 CSV writer for list exports. Text cells that a spreadsheet
 * would read as a formula (OWASP CSV injection) get a leading apostrophe;
 * numbers are written as they are.
 */
public final class CsvWriter {

    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private final StringBuilder sb = new StringBuilder();

    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
        return this;
    }

    public CsvWriter rows(List<Object[]> rows) {
        rows.forEach(this::row);
        return this;
    }

    public String build() {
        return sb.toString();
    }

    private String escape(Object cell) {
        if (cell == null) {
            return "";
        }
        String value = String.valueOf(cell);
        if (!(cell instanceof Number) && !value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
            // text typed in by users (subject codes, free-text answers) must not run as a spreadsheet formula
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
