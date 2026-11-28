package eu.dtc4eu.common.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void textThatWouldRunAsAFormulaIsNeutralised() {
        String csv = new CsvWriter()
                .row("=HYPERLINK(\"http://x\",\"y\")", "+1", "-cmd", "@SUM(A1)", "\tx", "S-001")
                .build();
        assertThat(csv).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\",'+1,'-cmd,'@SUM(A1),'\tx,S-001\r\n");
    }

    @Test
    void numbersAndPlainTextAreWrittenAsTheyAre() {
        String csv = new CsvWriter().row(-1.5, new BigDecimal("-2"), 42L, "a,b", "", null).build();
        assertThat(csv).isEqualTo("-1.5,-2,42,\"a,b\",,\r\n");
    }
}
