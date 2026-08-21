package eu.dtc4eu.website.common.web;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;

/**
 * Sanitizes CMS rich-text HTML on every write. The public site renders the
 * stored HTML directly, so this service is the only line of defence — keep
 * the policy strict: basic formatting, headings, lists, blockquotes and
 * http(s)/mailto links only. No images, iframes, styles or event handlers.
 */
@Service
public class HtmlSanitizerService {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(new HtmlPolicyBuilder()
                    .allowElements("a", "blockquote")
                    .allowUrlProtocols("http", "https", "mailto")
                    .allowAttributes("href").onElements("a")
                    .requireRelNofollowOnLinks()
                    .toFactory());

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return POLICY.sanitize(html);
    }
}
