package eu.dtc4eu.website.news.repository;

import eu.dtc4eu.website.news.entity.NewsPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {

    Optional<NewsPost> findBySlugAndLocale(String slug, String locale);

    boolean existsBySlugAndLocale(String slug, String locale);

    List<NewsPost> findByLocaleOrderByCreatedAtDesc(String locale);

    Page<NewsPost> findByLocaleAndStatusOrderByPublishedAtDesc(String locale, NewsPost.Status status,
                                                               Pageable pageable);
}
