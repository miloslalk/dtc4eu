package eu.dtc4eu.website.gallery.repository;

import eu.dtc4eu.website.gallery.entity.Gallery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {

    Optional<Gallery> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
