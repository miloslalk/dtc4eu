package eu.dtc4eu.website.gallery.controller;

import eu.dtc4eu.website.gallery.dto.GalleryDtos;
import eu.dtc4eu.website.gallery.service.GalleryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/galleries")
@RequiredArgsConstructor
public class GalleryAdminController {

    private final GalleryService galleryService;

    @Operation(summary = "List galleries with images")
    @GetMapping
    public List<GalleryDtos.GalleryDto> list() {
        return galleryService.list();
    }

    @GetMapping("/{id}")
    public GalleryDtos.GalleryDto get(@PathVariable Long id) {
        return galleryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GalleryDtos.GalleryDto create(@Valid @RequestBody GalleryDtos.SaveGalleryRequest request) {
        return galleryService.create(request);
    }

    @PutMapping("/{id}")
    public GalleryDtos.GalleryDto update(@PathVariable Long id,
                                         @Valid @RequestBody GalleryDtos.SaveGalleryRequest request) {
        return galleryService.update(id, request);
    }

    @Operation(summary = "Replace the gallery's ordered image list atomically")
    @PutMapping("/{id}/images")
    public GalleryDtos.GalleryDto saveImages(@PathVariable Long id,
                                             @Valid @RequestBody GalleryDtos.SaveImagesRequest request) {
        return galleryService.saveImages(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        galleryService.delete(id);
    }
}
