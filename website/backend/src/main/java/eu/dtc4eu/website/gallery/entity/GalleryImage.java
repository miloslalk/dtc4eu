package eu.dtc4eu.website.gallery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gallery_images")
@Getter
@Setter
@NoArgsConstructor
public class GalleryImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gallery_id", nullable = false)
    private Long galleryId;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(length = 300)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
