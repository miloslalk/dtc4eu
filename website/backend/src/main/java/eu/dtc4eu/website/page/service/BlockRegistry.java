package eu.dtc4eu.website.page.service;

import eu.dtc4eu.website.page.entity.BlockType;

import java.util.List;
import java.util.Map;

/**
 * Declares the payload shape of every block type. BlockValidationService
 * enforces these specs on write; the admin UI mirrors them as edit forms and
 * the public site as render components — the three must stay in sync when a
 * new block type is added.
 */
public final class BlockRegistry {

    public enum Kind {STRING, URL, HTML, INTEGER, BOOLEAN, MEDIA_ID, REFERENCE_ID, ENUM, ITEMS}

    public record Field(String key, Kind kind, boolean required, int maxLength, List<String> options,
                        List<Field> itemFields) {

        static Field string(String key, boolean required, int maxLength) {
            return new Field(key, Kind.STRING, required, maxLength, null, null);
        }

        /** A link target rendered as href: a string restricted to {@link eu.dtc4eu.website.common.web.SafeUrl}. */
        static Field url(String key, boolean required) {
            return new Field(key, Kind.URL, required, 300, null, null);
        }

        static Field html(String key, boolean required) {
            return new Field(key, Kind.HTML, required, 100_000, null, null);
        }

        static Field integer(String key, boolean required) {
            return new Field(key, Kind.INTEGER, required, 0, null, null);
        }

        static Field mediaId(String key) {
            return new Field(key, Kind.MEDIA_ID, false, 0, null, null);
        }

        static Field referenceId(String key, boolean required) {
            return new Field(key, Kind.REFERENCE_ID, required, 0, null, null);
        }

        static Field oneOf(String key, boolean required, List<String> options) {
            return new Field(key, Kind.ENUM, required, 0, options, null);
        }

        static Field items(String key, boolean required, List<Field> itemFields) {
            return new Field(key, Kind.ITEMS, required, 0, null, itemFields);
        }
    }

    public static final Map<BlockType, List<Field>> SPECS = Map.ofEntries(
            Map.entry(BlockType.HERO, List.of(
                    Field.string("kicker", false, 80),
                    Field.string("heading", true, 200),
                    Field.string("subheading", false, 500),
                    Field.string("ctaLabel", false, 60),
                    Field.url("ctaHref", false),
                    Field.mediaId("imageMediaId"))),
            Map.entry(BlockType.RICH_TEXT, List.of(
                    Field.string("heading", false, 200),
                    Field.html("bodyHtml", true))),
            Map.entry(BlockType.IMAGE_TEXT, List.of(
                    Field.string("heading", true, 200),
                    Field.html("bodyHtml", true),
                    Field.mediaId("imageMediaId"),
                    Field.oneOf("imageSide", false, List.of("LEFT", "RIGHT")))),
            Map.entry(BlockType.CARDS, List.of(
                    Field.string("heading", false, 200),
                    Field.items("items", true, List.of(
                            Field.string("title", true, 120),
                            Field.html("bodyHtml", false),
                            Field.string("icon", false, 20))))),
            Map.entry(BlockType.FACTS, List.of(
                    Field.string("heading", false, 200),
                    Field.items("items", true, List.of(
                            Field.string("label", true, 120),
                            Field.string("value", true, 300))))),
            Map.entry(BlockType.PARTNERS, List.of(
                    Field.string("heading", false, 200),
                    Field.items("items", true, List.of(
                            Field.string("name", true, 160),
                            Field.url("url", false),
                            Field.string("country", false, 80),
                            Field.mediaId("logoMediaId"))))),
            Map.entry(BlockType.GALLERY, List.of(
                    Field.string("heading", false, 200),
                    Field.referenceId("galleryId", true))),
            Map.entry(BlockType.NEWS_LIST, List.of(
                    Field.string("heading", false, 200),
                    Field.integer("limit", false))),
            Map.entry(BlockType.DOWNLOADS, List.of(
                    Field.string("heading", false, 200),
                    Field.items("items", true, List.of(
                            Field.string("title", true, 200),
                            Field.string("description", false, 400),
                            Field.referenceId("mediaId", true))))),
            Map.entry(BlockType.CTA, List.of(
                    Field.string("heading", true, 200),
                    Field.html("bodyHtml", false),
                    Field.string("buttonLabel", true, 60),
                    Field.url("buttonHref", true))),
            Map.entry(BlockType.CONTACT_FORM, List.of(
                    Field.string("heading", false, 200),
                    Field.string("intro", false, 500))));

    private BlockRegistry() {
    }
}
