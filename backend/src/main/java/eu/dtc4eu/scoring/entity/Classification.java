package eu.dtc4eu.scoring.entity;

import java.math.BigDecimal;

/**
 * Effectiveness classification (v0.2 register §3.3, TAB.5). The thresholds are
 * per questionnaire ({@link Bands}); boundary semantics are exact — on DTCRO,
 * 2.0 is CRITICAL and 3.0 is EFFICACY.
 */
public enum Classification {

    /** 1 ≤ M ≤ 2 — Unacceptable. */
    CRITICAL,
    /** 2 &lt; M &lt; 3 — Partially Acceptable. */
    INTERMEDIATE,
    /** 3 ≤ M — More than Acceptable. */
    EFFICACY;

    /**
     * A questionnaire's TAB.5 band table: CRITICAL when M &le; criticalMax,
     * EFFICACY when M &ge; efficacyMin, INTERMEDIATE in between. Stored per
     * questionnaire ({@code questionnaires.band_*}); null = not yet supplied
     * by the client, so nothing is classified.
     */
    public record Bands(BigDecimal criticalMax, BigDecimal efficacyMin) {

        /** DTCRO/MHSRO on the 1–4 scale (register §3.3). */
        public static final Bands DTCRO = new Bands(BigDecimal.valueOf(2), BigDecimal.valueOf(3));

        public static Bands of(BigDecimal criticalMax, BigDecimal efficacyMin) {
            return criticalMax == null || efficacyMin == null ? null : new Bands(criticalMax, efficacyMin);
        }
    }

    /** Classifies on the DTCRO 1–4 bands (also used for QCNC standard scores, which share TAB.5). */
    public static Classification of(BigDecimal score) {
        return of(score, Bands.DTCRO);
    }

    public static Classification of(BigDecimal score, Bands bands) {
        if (score == null || bands == null) {
            return null;
        }
        if (score.compareTo(bands.criticalMax()) <= 0) {
            return CRITICAL;
        }
        if (score.compareTo(bands.efficacyMin()) < 0) {
            return INTERMEDIATE;
        }
        return EFFICACY;
    }
}
