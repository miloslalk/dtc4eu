package eu.dtc4eu.website.news.controller;

import eu.dtc4eu.website.news.dto.NewsDtos;
import eu.dtc4eu.website.news.entity.NewsPost;
import eu.dtc4eu.website.news.service.NewsService;
import eu.dtc4eu.website.security.AdminUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/news")
@RequiredArgsConstructor
public class NewsAdminController {

    private final NewsService newsService;

    @Operation(summary = "List news posts, newest first")
    @GetMapping
    public List<NewsDtos.NewsDto> list() {
        return newsService.list();
    }

    @GetMapping("/{id}")
    public NewsDtos.NewsDto get(@PathVariable Long id) {
        return newsService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NewsDtos.NewsDto create(@Valid @RequestBody NewsDtos.SaveNewsRequest request,
                                   @AuthenticationPrincipal AdminUserDetails principal) {
        return newsService.create(request, principal.getId());
    }

    @PutMapping("/{id}")
    public NewsDtos.NewsDto update(@PathVariable Long id, @Valid @RequestBody NewsDtos.SaveNewsRequest request,
                                   @AuthenticationPrincipal AdminUserDetails principal) {
        return newsService.update(id, request, principal.getId());
    }

    @PostMapping("/{id}/publish")
    public NewsDtos.NewsDto publish(@PathVariable Long id, @AuthenticationPrincipal AdminUserDetails principal) {
        return newsService.setStatus(id, NewsPost.Status.PUBLISHED, principal.getId());
    }

    @PostMapping("/{id}/unpublish")
    public NewsDtos.NewsDto unpublish(@PathVariable Long id, @AuthenticationPrincipal AdminUserDetails principal) {
        return newsService.setStatus(id, NewsPost.Status.DRAFT, principal.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        newsService.delete(id);
    }
}
