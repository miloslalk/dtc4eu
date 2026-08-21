package eu.dtc4eu.website.page.service;

import eu.dtc4eu.website.common.error.BadRequestException;
import eu.dtc4eu.website.common.web.HtmlSanitizerService;
import eu.dtc4eu.website.page.entity.BlockType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockValidationServiceTest {

    private final BlockValidationService service = new BlockValidationService(new HtmlSanitizerService());

    @Test
    void validHeroPayloadIsNormalized() {
        Map<String, Object> normalized = service.validate(BlockType.HERO, Map.of(
                "heading", "  Welcome  ",
                "kicker", "Erasmus+",
                "ctaHref", "/the-project"));
        assertThat(normalized).containsEntry("heading", "Welcome")
                .containsEntry("kicker", "Erasmus+")
                .doesNotContainKey("subheading");
    }

    @Test
    void unknownKeysAreRejected() {
        assertThatThrownBy(() -> service.validate(BlockType.HERO, Map.of("heading", "x", "hax", "y")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("unknown field 'hax'");
    }

    @Test
    void missingRequiredFieldIsRejected() {
        assertThatThrownBy(() -> service.validate(BlockType.CTA, Map.of("heading", "x", "buttonLabel", "Go")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("buttonHref is required");
    }

    @Test
    void htmlFieldsAreSanitized() {
        Map<String, Object> normalized = service.validate(BlockType.RICH_TEXT, Map.of(
                "bodyHtml", "<p>ok</p><script>alert(1)</script><img src=x onerror=alert(1)>"));
        String html = (String) normalized.get("bodyHtml");
        assertThat(html).contains("<p>ok</p>").doesNotContain("script").doesNotContain("img");
    }

    @Test
    void itemListsAreValidatedPerEntry() {
        assertThatThrownBy(() -> service.validate(BlockType.FACTS, Map.of(
                "items", List.of(Map.of("label", "Programme"))))) // value missing
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("items[0].value is required");
    }

    @Test
    void enumFieldRejectsUnknownOption() {
        assertThatThrownBy(() -> service.validate(BlockType.IMAGE_TEXT, Map.of(
                "heading", "x", "bodyHtml", "<p>y</p>", "imageSide", "TOP")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("imageSide must be one of");
    }

    @Test
    void linksGetNofollowButSurvive() {
        Map<String, Object> normalized = service.validate(BlockType.RICH_TEXT, Map.of(
                "bodyHtml", "<p><a href=\"https://example.eu\">link</a></p>"));
        assertThat((String) normalized.get("bodyHtml")).contains("href=\"https://example.eu\"");
    }
}
