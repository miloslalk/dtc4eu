package eu.dtc4eu.website.gallery.dto;

import eu.dtc4eu.website.gallery.entity.Gallery;
import eu.dtc4eu.website.gallery.entity.GalleryImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class GalleryDtos {

    private GalleryDtos() {
    }

    public record ImageDto(Long id, Long mediaId, String caption, String url, String altText) {

        public static ImageDto of(GalleryImage image, String altText) {
            return new ImageDto(image.getId(), image.getMediaId(), image.getCaption(),
                    "/api/public/media/" + image.getMediaId(), altText);
        }
    }

    public record GalleryDto(Long id, String slug, String name, String description, List<ImageDto> images) {

        public static GalleryDto of(Gallery gallery, List<ImageDto> images) {
            return new GalleryDto(gallery.getId(), gallery.getSlug(), gallery.getName(), gallery.getDescription(),
                    images);
        }
    }

    public record SaveGalleryRequest(
            @NotBlank @Size(max = 120) String slug,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String description) {
    }

    public record SaveImageRequest(@NotNull Long mediaId, @Size(max = 300) String caption) {
    }

    public record SaveImagesRequest(@NotNull @Valid List<SaveImageRequest> images) {
    }
}
