package eu.dtc4eu.website.page.repository;

import eu.dtc4eu.website.page.entity.PageBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PageBlockRepository extends JpaRepository<PageBlock, Long> {

    List<PageBlock> findByPageIdOrderBySortOrderAsc(Long pageId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from PageBlock b where b.pageId = :pageId")
    void deleteByPageId(@Param("pageId") Long pageId);
}
