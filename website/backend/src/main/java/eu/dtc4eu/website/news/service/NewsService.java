package eu.dtc4eu.website.news.service;

import eu.dtc4eu.website.common.error.ConflictException;
import eu.dtc4eu.website.common.error.NotFoundException;
import eu.dtc4eu.website.common.web.HtmlSanitizerService;
import eu.dtc4eu.website.common.web.SlugUtil;
import eu.dtc4eu.website.news.dto.NewsDtos;
import eu.dtc4eu.website.news.entity.NewsPost;
import eu.dtc4eu.website.news.repository.NewsPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {

    public static final String DEFAULT_LOCALE = "en";

    private final NewsPostRepository newsPostRepository;
    private final HtmlSanitizerService htmlSanitizer;

    @Transactional(readOnly = true)
    public List<NewsDtos.NewsDto> list() {
        return newsPostRepository.findByLocaleOrderByCreatedAtDesc(DEFAULT_LOCALE).stream()
                .map(NewsDtos.NewsDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public NewsDtos.NewsDto get(Long id) {
        return NewsDtos.NewsDto.of(find(id));
    }

    @Transactional
    public NewsDtos.NewsDto create(NewsDtos.SaveNewsRequest request, Long actorId) {
        NewsPost post = new NewsPost();
        apply(post, request, actorId, true);
        return NewsDtos.NewsDto.of(newsPostRepository.save(post));
    }

    @Transactional
    public NewsDtos.NewsDto update(Long id, NewsDtos.SaveNewsRequest request, Long actorId) {
        NewsPost post = find(id);
        apply(post, request, actorId, !post.getSlug().equals(SlugUtil.normalize(request.slug())));
        return NewsDtos.NewsDto.of(post);
    }

    @Transactional
    public NewsDtos.NewsDto setStatus(Long id, NewsPost.Status status, Long actorId) {
        NewsPost post = find(id);
        post.setStatus(status);
        if (status == NewsPost.Status.PUBLISHED && post.getPublishedAt() == null) {
            post.setPublishedAt(OffsetDateTime.now());
        }
        post.setUpdatedBy(actorId);
        return NewsDtos.NewsDto.of(post);
    }

    @Transactional
    public void delete(Long id) {
        newsPostRepository.delete(find(id));
    }

    private NewsPost find(Long id) {
        return newsPostRepository.findById(id).orElseThrow(() -> NotFoundException.of("News post", id));
    }

    private void apply(NewsPost post, NewsDtos.SaveNewsRequest request, Long actorId, boolean slugChanged) {
        String slug = SlugUtil.normalize(request.slug());
        if (slugChanged && newsPostRepository.existsBySlugAndLocale(slug, DEFAULT_LOCALE)) {
            throw new ConflictException("A news post with slug '" + slug + "' already exists");
        }
        post.setSlug(slug);
        post.setLocale(DEFAULT_LOCALE);
        post.setTitle(request.title());
        post.setExcerpt(request.excerpt());
        post.setBodyHtml(htmlSanitizer.sanitize(request.bodyHtml()));
        post.setCoverMediaId(request.coverMediaId());
        post.setUpdatedBy(actorId);
    }
}
