package eu.dtc4eu.website.news.dto;

import eu.dtc4eu.website.news.entity.NewsPost;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class NewsDtos {

    private NewsDtos() {
    }

    public record NewsDto(Long id, String slug, String title, String excerpt, String bodyHtml, Long coverMediaId,
                          NewsPost.Status status, OffsetDateTime publishedAt, OffsetDateTime updatedAt) {

        public static NewsDto of(NewsPost post) {
            return new NewsDto(post.getId(), post.getSlug(), post.getTitle(), post.getExcerpt(), post.getBodyHtml(),
                    post.getCoverMediaId(), post.getStatus(), post.getPublishedAt(), post.getUpdatedAt());
        }
    }

    public record SaveNewsRequest(
            @NotBlank @Size(max = 120) String slug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String excerpt,
            String bodyHtml,
            Long coverMediaId) {
    }
}
