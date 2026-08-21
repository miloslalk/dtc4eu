package eu.dtc4eu.website.media.repository;

import eu.dtc4eu.website.media.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findAllByOrderByIdDesc();
}
