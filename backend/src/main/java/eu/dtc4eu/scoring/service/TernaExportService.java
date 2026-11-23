package eu.dtc4eu.scoring.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.scoring.dto.ScoringDtos.AreaScoreRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.FacilityScoresDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.ItemScoreRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.PopulationCellDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.PopulationRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.TernaBenchmarkRowDto;
import eu.dtc4eu.scoring.dto.ScoringDtos.TernaPopulationDto;
import eu.dtc4eu.scoring.entity.Classification;
import eu.dtc4eu.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Terna report workbook (spec REP-02) in the layout of {@code Terna Example1…xlsx}:
 * one sheet per facility with TAB.3 (Quant-SPWP, item level), TAB.4
 * (Quant-SAWA, area level) and the facility average, the terna's Benchmark
 * Strength Areas beside them, and a TAB.2 population sheet. Values are the
 * engine's stored aggregates — the workbook carries numbers, not formulas.
 */
@Service
@RequiredArgsConstructor
public class TernaExportService {

    private static final String[] GRID_COLUMNS =
            {"U-I", "F-I", "S-I", "M-I", "Aver-I", "Com", "Del", "Final", "Level"};
    private static final String[] CATEGORIES = {"USER", "FAMILY", "STAFF", "MANAGER"};

    private final TernaAnalyticsService ternaAnalyticsService;
    private final FacilityScoringService facilityScoringService;
    private final AuditService auditService;

    public record TernaWorkbook(String fileName, byte[] content) {
    }

    @Transactional
    public TernaWorkbook export(Long ternaId, Long questionnaireId, AppUserDetails principal) {
        TernaPopulationDto population = ternaAnalyticsService.population(ternaId, questionnaireId, principal);
        List<TernaBenchmarkRowDto> benchmarks = ternaAnalyticsService.benchmarks(ternaId, questionnaireId, principal);

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            String questionnaireCode = null;
            for (PopulationRowDto member : population.facilities()) {
                FacilityScoresDto scores = facilityScoringService.getScores(member.facilityId(), questionnaireId,
                        principal);
                questionnaireCode = scores.questionnaireCode();
                facilitySheet(workbook, styles, scores, benchmarks);
            }
            populationSheet(workbook, styles, population);

            workbook.write(out);
            auditService.success(principal.getId(), "EXPORT", "terna", ternaId,
                    Map.of("format", "xlsx", "questionnaireId", questionnaireId));
            String name = population.ternaCode() + "-" + (questionnaireCode != null ? questionnaireCode : "scores")
                    + "-terna-report.xlsx";
            return new TernaWorkbook(name, out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the terna workbook", e);
        }
    }

    private void facilitySheet(XSSFWorkbook workbook, Styles styles, FacilityScoresDto scores,
                               List<TernaBenchmarkRowDto> benchmarks) {
        Sheet sheet = workbook.createSheet(safeSheetName(scores.facilityCode()));
        int r = 0;
        title(sheet, r++, "Quantitative Analysis of " + scores.questionnaireCode() + " — " + scores.facilityCode(),
                styles);
        r++;

        title(sheet, r++, "TAB.3 Quant-SPWP-R-Tab (item level)", styles);
        header(sheet, r++, "Coordinate", styles);
        for (ItemScoreRowDto item : scores.items()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(item.coordinate());
            grid(row, styles, item.userMean(), item.familyMean(), item.staffMean(), item.managerMean(),
                    item.averI(), item.comValue(), item.delValue(), item.finalScore(), item.classification());
        }
        r++;

        title(sheet, r++, "TAB.4 Quant-SAWA-R-Tab (area level)", styles);
        int areaHeaderRow = r;
        header(sheet, r++, "Area", styles);
        for (AreaScoreRowDto area : scores.areas()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(area.areaNo() + ". " + nullToEmpty(area.areaName()));
            grid(row, styles, area.userMean(), area.familyMean(), area.staffMean(), area.managerMean(),
                    area.averI(), area.comValue(), area.delValue(), area.finalScore(), area.classification());
        }
        Row average = sheet.createRow(r);
        Cell label = average.createCell(0);
        label.setCellValue("Mean of all areas");
        label.setCellStyle(styles.bold);
        number(average, 8, scores.overallScore(), styles);
        level(average, 9, scores.overallClassification(), styles);

        // the workbook's right-hand table: Benchmark Strength Areas of the terna
        int col = GRID_COLUMNS.length + 2;
        Row benchTitle = rowAt(sheet, areaHeaderRow - 1);
        Cell titleCell = benchTitle.createCell(col);
        titleCell.setCellValue("Benchmark Strength Areas of the Terna");
        titleCell.setCellStyle(styles.bold);
        Row benchHeader = rowAt(sheet, areaHeaderRow);
        String[] headers = {"Area", "Best facility", "Score", "Level"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = benchHeader.createCell(col + i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }
        int b = areaHeaderRow + 1;
        for (TernaBenchmarkRowDto bench : benchmarks) {
            Row row = rowAt(sheet, b++);
            row.createCell(col).setCellValue(bench.areaNo() + ". " + nullToEmpty(bench.areaName()));
            row.createCell(col + 1).setCellValue(nullToEmpty(bench.bestFacilityCode()));
            number(row, col + 2, bench.bestScore(), styles);
            level(row, col + 3, bench.classification(), styles);
        }

        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(col, 34 * 256);
        sheet.setColumnWidth(col + 1, 14 * 256);
        sheet.setColumnWidth(GRID_COLUMNS.length, 14 * 256);
        sheet.setColumnWidth(col + 3, 14 * 256);
    }

    private void populationSheet(XSSFWorkbook workbook, Styles styles, TernaPopulationDto population) {
        Sheet sheet = workbook.createSheet("TAB.2 Population");
        title(sheet, 0, "TAB.2 Terna-Com-Pop-Tab — collected questionnaires / participating subjects", styles);
        Row head = sheet.createRow(2);
        String[] headers = {"Facility", "Users", "Familiars", "Staff", "Managers", "Total"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = head.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }
        int r = 3;
        for (PopulationRowDto row : population.facilities()) {
            populationRow(sheet.createRow(r++), row.facilityCode(), row, styles.plain);
        }
        populationRow(sheet.createRow(r), "Total", population.totals(), styles.bold);
        sheet.setColumnWidth(0, 18 * 256);
    }

    private static void populationRow(Row row, String label, PopulationRowDto data, CellStyle style) {
        Cell first = row.createCell(0);
        first.setCellValue(label);
        first.setCellStyle(style);
        for (int i = 0; i < CATEGORIES.length; i++) {
            PopulationCellDto cell = data.byCategory().get(CATEGORIES[i]);
            row.createCell(i + 1).setCellValue(cell == null ? "" : cell.collected() + " / " + cell.roster());
        }
        row.createCell(CATEGORIES.length + 1).setCellValue(data.collectedTotal() + " / " + data.rosterTotal());
    }

    /* ---------------------------------- cells ---------------------------------- */

    private static void grid(Row row, Styles styles, BigDecimal u, BigDecimal f, BigDecimal s, BigDecimal m,
                             BigDecimal aver, BigDecimal com, BigDecimal del, BigDecimal fin,
                             Classification classification) {
        BigDecimal[] values = {u, f, s, m, aver, com, del, fin};
        for (int i = 0; i < values.length; i++) {
            number(row, i + 1, values[i], styles);
        }
        level(row, values.length + 1, classification, styles);
    }

    private static void number(Row row, int col, BigDecimal value, Styles styles) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(styles.number);
        }
    }

    private static void level(Row row, int col, Classification classification, Styles styles) {
        if (classification == null) {
            return;
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(classification.name());
        cell.setCellStyle(styles.levels.get(classification));
    }

    private static void title(Sheet sheet, int r, String text, Styles styles) {
        Cell cell = rowAt(sheet, r).createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(styles.bold);
    }

    private static void header(Sheet sheet, int r, String first, Styles styles) {
        Row row = rowAt(sheet, r);
        Cell cell = row.createCell(0);
        cell.setCellValue(first);
        cell.setCellStyle(styles.header);
        for (int i = 0; i < GRID_COLUMNS.length; i++) {
            Cell c = row.createCell(i + 1);
            c.setCellValue(GRID_COLUMNS[i]);
            c.setCellStyle(styles.header);
        }
    }

    private static Row rowAt(Sheet sheet, int r) {
        Row row = sheet.getRow(r);
        return row != null ? row : sheet.createRow(r);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Excel sheet names: ≤ 31 chars, none of : \ / ? * [ ]. */
    static String safeSheetName(String name) {
        String cleaned = (name == null || name.isBlank() ? "Facility" : name).replaceAll("[:\\\\/?*\\[\\]]", "-");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private static final class Styles {
        final CellStyle plain;
        final CellStyle bold;
        final CellStyle header;
        final CellStyle number;
        final Map<Classification, CellStyle> levels = new EnumMap<>(Classification.class);

        Styles(XSSFWorkbook workbook) {
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            plain = workbook.createCellStyle();
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);
            header = workbook.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            number = workbook.createCellStyle();
            number.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            levels.put(Classification.CRITICAL, fill(workbook, IndexedColors.ROSE));
            levels.put(Classification.INTERMEDIATE, fill(workbook, IndexedColors.LIGHT_YELLOW));
            levels.put(Classification.EFFICACY, fill(workbook, IndexedColors.LIGHT_GREEN));
        }

        private static CellStyle fill(XSSFWorkbook workbook, IndexedColors color) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(color.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }
    }
}
