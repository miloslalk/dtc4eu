package eu.dtc4eu.website.media.service;

import eu.dtc4eu.website.common.error.BadRequestException;
import eu.dtc4eu.website.common.error.NotFoundException;
import eu.dtc4eu.website.config.AppProperties;
import eu.dtc4eu.website.gallery.repository.GalleryImageRepository;
import eu.dtc4eu.website.media.entity.MediaAsset;
import eu.dtc4eu.website.media.repository.MediaAssetRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Media library backing store. Objects live in the website's own bucket
 * (dtc4eu-website); the bucket is created on startup when missing so a fresh
 * dev environment works without manual MinIO setup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml",
            "application/pdf");

    private final S3Client s3Client;
    private final AppProperties appProperties;
    private final MediaAssetRepository mediaAssetRepository;
    private final GalleryImageRepository galleryImageRepository;

    @PostConstruct
    void ensureBucket() {
        String bucket = appProperties.storage().bucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created storage bucket {}", bucket);
        } catch (Exception e) {
            // storage may be unreachable at boot (e.g. tests without MinIO) — uploads will fail loudly instead
            log.warn("Could not verify storage bucket {}: {}", bucket, e.getMessage());
        }
    }

    @Transactional
    public MediaAsset upload(MultipartFile file, String altText, Long uploadedBy) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("Unsupported file type: " + contentType);
        }
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String key = UUID.randomUUID() + "-" + original.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(appProperties.storage().bucket())
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file");
        }
        MediaAsset asset = new MediaAsset();
        asset.setFilename(original);
        asset.setContentType(contentType);
        asset.setSizeBytes(file.getSize());
        asset.setStorageKey(key);
        asset.setAltText(altText);
        asset.setCreatedBy(uploadedBy);
        return mediaAssetRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> list() {
        return mediaAssetRepository.findAllByOrderByIdDesc();
    }

    @Transactional
    public void updateAltText(Long id, String altText) {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Media asset", id));
        asset.setAltText(altText);
    }

    @Transactional
    public void delete(Long id) {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Media asset", id));
        if (galleryImageRepository.existsByMediaId(id)) {
            throw new BadRequestException("Media is used in a gallery — remove it there first");
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(appProperties.storage().bucket())
                .key(asset.getStorageKey())
                .build());
        mediaAssetRepository.delete(asset);
    }

    /** Streams the object for both the admin library and the public site. */
    @Transactional(readOnly = true)
    public StreamedMedia stream(Long id) {
        MediaAsset asset = mediaAssetRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Media asset", id));
        var object = s3Client.getObject(GetObjectRequest.builder()
                .bucket(appProperties.storage().bucket())
                .key(asset.getStorageKey())
                .build());
        return new StreamedMedia(asset, new InputStreamResource(object));
    }

    public record StreamedMedia(MediaAsset asset, InputStreamResource resource) {
    }
}
