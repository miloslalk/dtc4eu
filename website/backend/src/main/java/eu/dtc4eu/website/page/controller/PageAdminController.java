package eu.dtc4eu.website.page.controller;

import eu.dtc4eu.website.page.dto.PageDtos;
import eu.dtc4eu.website.page.entity.Page;
import eu.dtc4eu.website.page.service.PageService;
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
@RequestMapping("/api/admin/pages")
@RequiredArgsConstructor
public class PageAdminController {

    private final PageService pageService;

    @Operation(summary = "List pages (nav order)")
    @GetMapping
    public List<PageDtos.PageSummaryDto> list() {
        return pageService.list();
    }

    @Operation(summary = "Page detail with ordered blocks")
    @GetMapping("/{id}")
    public PageDtos.PageDetailDto get(@PathVariable Long id) {
        return pageService.get(id);
    }

    @Operation(summary = "Create a page (starts DRAFT with no blocks)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PageDtos.PageDetailDto create(@Valid @RequestBody PageDtos.SavePageRequest request,
                                         @AuthenticationPrincipal AdminUserDetails principal) {
        return pageService.create(request, principal.getId());
    }

    @Operation(summary = "Update page metadata")
    @PutMapping("/{id}")
    public PageDtos.PageDetailDto update(@PathVariable Long id, @Valid @RequestBody PageDtos.SavePageRequest request,
                                         @AuthenticationPrincipal AdminUserDetails principal) {
        return pageService.update(id, request, principal.getId());
    }

    @Operation(summary = "Replace the page's ordered block list atomically")
    @PutMapping("/{id}/blocks")
    public PageDtos.PageDetailDto saveBlocks(@PathVariable Long id,
                                             @Valid @RequestBody PageDtos.SaveBlocksRequest request,
                                             @AuthenticationPrincipal AdminUserDetails principal) {
        return pageService.saveBlocks(id, request, principal.getId());
    }

    @Operation(summary = "Publish a page")
    @PostMapping("/{id}/publish")
    public PageDtos.PageDetailDto publish(@PathVariable Long id,
                                          @AuthenticationPrincipal AdminUserDetails principal) {
        return pageService.setStatus(id, Page.Status.PUBLISHED, principal.getId());
    }

    @Operation(summary = "Unpublish a page (back to DRAFT)")
    @PostMapping("/{id}/unpublish")
    public PageDtos.PageDetailDto unpublish(@PathVariable Long id,
                                            @AuthenticationPrincipal AdminUserDetails principal) {
        return pageService.setStatus(id, Page.Status.DRAFT, principal.getId());
    }

    @Operation(summary = "Delete a page and its blocks")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        pageService.delete(id);
    }
}
