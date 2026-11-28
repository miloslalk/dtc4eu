package eu.dtc4eu.security;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCutoffTest {

    @Test
    void tokensIssuedBeforeTheCutoffAreRefused() {
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-11-28T10:00:00Z");
        assertThat(TokenCutoff.accepts(null, new Date(0))).isTrue();
        assertThat(TokenCutoff.accepts(cutoff, Date.from(cutoff.toInstant()))).isTrue();
        assertThat(TokenCutoff.accepts(cutoff, Date.from(cutoff.toInstant().plusSeconds(1)))).isTrue();
        assertThat(TokenCutoff.accepts(cutoff, Date.from(cutoff.toInstant().minusSeconds(1)))).isFalse();
        assertThat(TokenCutoff.accepts(cutoff, null)).isFalse();
        assertThat(TokenCutoff.now().getNano()).isZero();
    }
}
