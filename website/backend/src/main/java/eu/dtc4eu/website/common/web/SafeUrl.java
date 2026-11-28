package eu.dtc4eu.website.common.web;

import java.util.regex.Pattern;

/**
 * Link targets the public site renders as {@code href}: same-site paths and
 * anchors, or http(s)/mailto — the protocols the rich-text sanitizer allows.
 * Anything else ({@code javascript:}, {@code data:}, protocol-relative
 * {@code //host}) would let a CMS editor run script in visitors' browsers.
 */
public final class SafeUrl {

    private static final Pattern ALLOWED = Pattern.compile("^(?:https?://|mailto:|#|/(?![/\\\\]))",
            Pattern.CASE_INSENSITIVE);

    private SafeUrl() {
    }

    public static boolean isSafe(String url) {
        return url != null && ALLOWED.matcher(url).find();
    }
}
