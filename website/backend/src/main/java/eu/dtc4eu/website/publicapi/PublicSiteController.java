package eu.dtc4eu.website.publicapi;

import eu.dtc4eu.website.common.error.NotFoundException;
import eu.dtc4eu.website.contact.dto.ContactDtos;
import eu.dtc4eu.website.contact.service.ContactService;
import eu.dtc4eu.website.gallery.dto.GalleryDtos;
import eu.dtc4eu.website.gallery.service.GalleryService;
import eu.dtc4eu.website.media.service.MediaService;
import eu.dtc4eu.website.news.entity.NewsPost;
import eu.dtc4eu.website.news.repository.NewsPostRepository;
import eu.dtc4eu.website.page.dto.PageDtos;
import eu.dtc4eu.website.page.entity.Page;
import eu.dtc4eu.website.page.repository.PageBlockRepository;
import eu.dtc4eu.website.page.repository.PageRepository;
import eu.dtc4eu.website.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The anonymous content API consumed by the public site. Only PUBLISHED
 * content is ever returned; draft pages and posts 404 exactly like missing
 * ones. Responses carry short public cache headers.
 */
@RestController
@RequestMapping("/api/public")
@CrossOrigin
@RequiredArgsConstructor
public class PublicSiteController {

    private static final String LOCALE = "en";
    private static final String CACHE_CONTROL = "public, max-age=60";

    private final PageRepository pageRepository;
    private final PageBlockRepository pageBlockRepository;
    private final NewsPostRepository newsPostRepository;
    private final GalleryService galleryService;
    private final MediaService mediaService;
    private final SettingsService settingsService;
    private final ContactService contactService;

    public record NavItem(String slug, String label) {
    }

    public record PublicNewsItem(String slug, String title, String excerpt, Long coverMediaId,
                                 OffsetDateTime publishedAt) {

        static PublicNewsItem of(NewsPost post) {
            return new PublicNewsItem(post.getSlug(), post.getTitle(), post.getExcerpt(), post.getCoverMediaId(),
                    post.getPublishedAt());
        }
    }

    public record PublicNewsDetail(String slug, String title, String excerpt, String bodyHtml, Long coverMediaId,
                                   OffsetDateTime publishedAt) {
    }

    public record PublicPage(String slug, String title, String seoTitle, String seoDescription,
                             List<PageDtos.BlockDto> blocks) {
    }

    @Operation(summary = "Navigation: published pages flagged show_in_nav, in nav order")
    @GetMapping("/nav")
    public ResponseEntity<List<NavItem>> nav() {
        List<NavItem> items = pageRepository
                .findByLocaleAndStatusAndShowInNavTrueOrderByNavOrderAscIdAsc(LOCALE, Page.Status.PUBLISHED)
                .stream()
                .map(page -> new NavItem(page.getSlug(),
                        page.getNavLabel() != null ? page.getNavLabel() : page.getTitle()))
                .toList();
        return cached(items);
    }

    @Operation(summary = "A published page with its ordered blocks")
    @GetMapping("/pages/{slug}")
    public ResponseEntity<PublicPage> page(@PathVariable String slug) {
        Page page = pageRepository.findBySlugAndLocale(slug, LOCALE)
                .filter(p -> p.getStatus() == Page.Status.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Page '" + slug + "' not found"));
        List<PageDtos.BlockDto> blocks = pageBlockRepository.findByPageIdOrderBySortOrderAsc(page.getId()).stream()
                .map(PageDtos.BlockDto::of)
                .toList();
        return cached(new PublicPage(page.getSlug(), page.getTitle(), page.getSeoTitle(), page.getSeoDescription(),
                blocks));
    }

    @Operation(summary = "Published news, newest first")
    @GetMapping("/news")
    public ResponseEntity<List<PublicNewsItem>> news(@RequestParam(defaultValue = "20") int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        List<PublicNewsItem> items = newsPostRepository
                .findByLocaleAndStatusOrderByPublishedAtDesc(LOCALE, NewsPost.Status.PUBLISHED,
                        PageRequest.of(0, size))
                .map(PublicNewsItem::of)
                .getContent();
        return cached(items);
    }

    @Operation(summary = "A published news post")
    @GetMapping("/news/{slug}")
    public ResponseEntity<PublicNewsDetail> newsDetail(@PathVariable String slug) {
        NewsPost post = newsPostRepository.findBySlugAndLocale(slug, LOCALE)
                .filter(p -> p.getStatus() == NewsPost.Status.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("News post '" + slug + "' not found"));
        return cached(new PublicNewsDetail(post.getSlug(), post.getTitle(), post.getExcerpt(), post.getBodyHtml(),
                post.getCoverMediaId(), post.getPublishedAt()));
    }

    @Operation(summary = "A gallery with its ordered images")
    @GetMapping("/galleries/{slug}")
    public ResponseEntity<GalleryDtos.GalleryDto> gallery(@PathVariable String slug) {
        return cached(galleryService.getBySlug(slug));
    }

    @Operation(summary = "A gallery by id (GALLERY page blocks reference galleries by id)")
    @GetMapping("/galleries/id/{id}")
    public ResponseEntity<GalleryDtos.GalleryDto> galleryById(@PathVariable Long id) {
        return cached(galleryService.get(id));
    }

    @Operation(summary = "Streams a media object (images, PDFs)")
    @GetMapping("/media/{id}")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> media(@PathVariable Long id) {
        MediaService.StreamedMedia streamed = mediaService.stream(id);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (needsSandbox(streamed.asset().getContentType())) {
            response.header("Content-Security-Policy", MEDIA_SANDBOX);
        }
        return response
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + streamed.asset().getFilename().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(streamed.asset().getContentType()))
                .body(streamed.resource());
    }

    @Operation(summary = "Site-wide settings (footer, disclaimer, contact email...)")
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Map<String, Object>>> settings() {
        return cached(settingsService.all());
    }

    @Operation(summary = "Submit the contact form")
    @PostMapping("/contact")
    public void contact(@Valid @RequestBody ContactDtos.SubmitRequest request) {
        contactService.submit(request);
    }

    /**
     * Uploads are served from the site's own origin, so an SVG opened directly
     * could run its scripts there; the sandbox stops that (an {@code <img>} never
     * runs them anyway). PDFs are exempt: browsers refuse to show a sandboxed PDF.
     */
    static final String MEDIA_SANDBOX = "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; sandbox";

    static boolean needsSandbox(String contentType) {
        return !"application/pdf".equals(contentType);
    }

    private <T> ResponseEntity<T> cached(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL).body(body);
    }
}
