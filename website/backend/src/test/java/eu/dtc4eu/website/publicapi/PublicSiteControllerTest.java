package eu.dtc4eu.website.publicapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicSiteControllerTest {

    @Test
    void uploadedMediaOtherThanPdfIsSandboxed() {
        assertThat(PublicSiteController.needsSandbox("image/svg+xml")).isTrue();
        assertThat(PublicSiteController.needsSandbox("image/png")).isTrue();
        assertThat(PublicSiteController.needsSandbox("application/pdf")).isFalse();
        assertThat(PublicSiteController.MEDIA_SANDBOX).contains("sandbox").contains("default-src 'none'")
                .doesNotContain("script-src");
    }
}
