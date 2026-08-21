package eu.dtc4eu.website.gallery.service;

import eu.dtc4eu.website.common.error.BadRequestException;
import eu.dtc4eu.website.common.error.ConflictException;
import eu.dtc4eu.website.common.error.NotFoundException;
import eu.dtc4eu.website.common.web.SlugUtil;
import eu.dtc4eu.website.gallery.dto.GalleryDtos;
import eu.dtc4eu.website.gallery.entity.Gallery;
import eu.dtc4eu.website.gallery.entity.GalleryImage;
import eu.dtc4eu.website.gallery.repository.GalleryImageRepository;
import eu.dtc4eu.website.gallery.repository.GalleryRepository;
import eu.dtc4eu.website.media.entity.MediaAsset;
import eu.dtc4eu.website.media.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GalleryService {

    private final GalleryRepository galleryRepository;
    private final GalleryImageRepository galleryImageRepository;
    private final MediaAssetRepository mediaAssetRepository;

    @Transactional(readOnly = true)
    public List<GalleryDtos.GalleryDto> list() {
        return galleryRepository.findAll().stream()
                .map(gallery -> GalleryDtos.GalleryDto.of(gallery, imagesOf(gallery.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GalleryDtos.GalleryDto get(Long id) {
        Gallery gallery = find(id);
        return GalleryDtos.GalleryDto.of(gallery, imagesOf(id));
    }

    @Transactional(readOnly = true)
    public GalleryDtos.GalleryDto getBySlug(String slug) {
        Gallery gallery = galleryRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Gallery '" + slug + "' not found"));
        return GalleryDtos.GalleryDto.of(gallery, imagesOf(gallery.getId()));
    }

    @Transactional
    public GalleryDtos.GalleryDto create(GalleryDtos.SaveGalleryRequest request) {
        Gallery gallery = new Gallery();
        apply(gallery, request, true);
        return GalleryDtos.GalleryDto.of(galleryRepository.save(gallery), List.of());
    }

    @Transactional
    public GalleryDtos.GalleryDto update(Long id, GalleryDtos.SaveGalleryRequest request) {
        Gallery gallery = find(id);
        apply(gallery, request, !gallery.getSlug().equals(SlugUtil.normalize(request.slug())));
        return GalleryDtos.GalleryDto.of(gallery, imagesOf(id));
    }

    /** Replaces the gallery's ordered image list atomically. */
    @Transactional
    public GalleryDtos.GalleryDto saveImages(Long id, GalleryDtos.SaveImagesRequest request) {
        Gallery gallery = find(id);
        for (GalleryDtos.SaveImageRequest image : request.images()) {
            if (!mediaAssetRepository.existsById(image.mediaId())) {
                throw new BadRequestException("Unknown media id " + image.mediaId());
            }
        }
        galleryImageRepository.deleteByGalleryId(id);
        List<GalleryImage> replacement = new ArrayList<>();
        for (int i = 0; i < request.images().size(); i++) {
            GalleryDtos.SaveImageRequest imageRequest = request.images().get(i);
            GalleryImage image = new GalleryImage();
            image.setGalleryId(id);
            image.setMediaId(imageRequest.mediaId());
            image.setCaption(imageRequest.caption());
            image.setSortOrder(i);
            replacement.add(image);
        }
        galleryImageRepository.saveAll(replacement);
        return GalleryDtos.GalleryDto.of(gallery, imagesOf(id));
    }

    @Transactional
    public void delete(Long id) {
        galleryRepository.delete(find(id));
    }

    private Gallery find(Long id) {
        return galleryRepository.findById(id).orElseThrow(() -> NotFoundException.of("Gallery", id));
    }

    private void apply(Gallery gallery, GalleryDtos.SaveGalleryRequest request, boolean slugChanged) {
        String slug = SlugUtil.normalize(request.slug());
        if (slugChanged && galleryRepository.existsBySlug(slug)) {
            throw new ConflictException("A gallery with slug '" + slug + "' already exists");
        }
        gallery.setSlug(slug);
        gallery.setName(request.name());
        gallery.setDescription(request.description());
    }

    private List<GalleryDtos.ImageDto> imagesOf(Long galleryId) {
        List<GalleryImage> images = galleryImageRepository.findByGalleryIdOrderBySortOrderAsc(galleryId);
        Map<Long, String> altById = mediaAssetRepository
                .findAllById(images.stream().map(GalleryImage::getMediaId).toList()).stream()
                .collect(Collectors.toMap(MediaAsset::getId,
                        asset -> asset.getAltText() != null ? asset.getAltText() : "",
                        (a, b) -> a));
        return images.stream()
                .map(image -> GalleryDtos.ImageDto.of(image, altById.getOrDefault(image.getMediaId(), "")))
                .toList();
    }
}
