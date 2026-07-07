package eu.dtc4eu.common.web;

import java.util.List;

/** Minimal RFC-4180 CSV writer for list exports. */
public final class CsvWriter {

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
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
