package eu.dtc4eu.website.gallery.repository;

import eu.dtc4eu.website.gallery.entity.GalleryImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {

    List<GalleryImage> findByGalleryIdOrderBySortOrderAsc(Long galleryId);

    boolean existsByMediaId(Long mediaId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from GalleryImage i where i.galleryId = :galleryId")
    void deleteByGalleryId(@Param("galleryId") Long galleryId);
}
