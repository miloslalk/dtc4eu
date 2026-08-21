package eu.dtc4eu.website.page.repository;

import eu.dtc4eu.website.page.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {

    Optional<Page> findBySlugAndLocale(String slug, String locale);

    boolean existsBySlugAndLocale(String slug, String locale);

    List<Page> findByLocaleOrderByNavOrderAscIdAsc(String locale);

    List<Page> findByLocaleAndStatusAndShowInNavTrueOrderByNavOrderAscIdAsc(String locale, Page.Status status);
}
