package eu.dtc4eu.website.common.web;

import java.text.Normalizer;
import java.util.Locale;

/** URL slug normalization shared by pages, news and galleries. */
public final class SlugUtil {

    private SlugUtil() {
    }

    public static String normalize(String input) {
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "page" : slug;
    }
}
