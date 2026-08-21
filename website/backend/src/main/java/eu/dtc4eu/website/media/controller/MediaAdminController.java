package eu.dtc4eu.website.media.controller;

import eu.dtc4eu.website.media.entity.MediaAsset;
import eu.dtc4eu.website.media.service.MediaService;
import eu.dtc4eu.website.security.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
public class MediaAdminController {

    private final MediaService mediaService;

    public record MediaDto(Long id, String filename, String contentType, long sizeBytes, String altText, String url) {

        static MediaDto of(MediaAsset asset) {
            return new MediaDto(asset.getId(), asset.getFilename(), asset.getContentType(), asset.getSizeBytes(),
                    asset.getAltText(), "/api/public/media/" + asset.getId());
        }
    }

    @Operation(summary = "List media assets, newest first")
    @GetMapping
    public List<MediaDto> list() {
        return mediaService.list().stream().map(MediaDto::of).toList();
    }

    @Operation(summary = "Upload a media file (images or PDF)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MediaDto upload(@RequestParam("file") MultipartFile file,
                           @RequestParam(value = "altText", required = false) String altText,
                           @AuthenticationPrincipal AdminUserDetails principal) {
        return MediaDto.of(mediaService.upload(file, altText, principal.getId()));
    }

    @Operation(summary = "Update alt text")
    @PutMapping("/{id}")
    public void updateAlt(@PathVariable Long id, @RequestBody Map<String, String> body) {
        mediaService.updateAltText(id, body.get("altText"));
    }

    @Operation(summary = "Delete an asset (blocked while referenced by a gallery)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        mediaService.delete(id);
    }
}
