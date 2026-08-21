package eu.dtc4eu.scoring.entity;

import java.math.BigDecimal;

/**
 * Effectiveness classification (v0.2 register §3.3, TAB.5). Boundary semantics
 * are exact: 2.0 is CRITICAL, 3.0 is EFFICACY.
 */
public enum Classification {

    /** 1 ≤ M ≤ 2 — Unacceptable. */
    CRITICAL,
    /** 2 &lt; M &lt; 3 — Partially Acceptable. */
    INTERMEDIATE,
    /** 3 ≤ M — More than Acceptable. */
    EFFICACY;

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    public static Classification of(BigDecimal score) {
        if (score == null) {
            return null;
        }
        if (score.compareTo(TWO) <= 0) {
            return CRITICAL;
        }
        if (score.compareTo(THREE) < 0) {
            return INTERMEDIATE;
        }
        return EFFICACY;
    }
}
