package eu.dtc4eu.website.page.service;

import eu.dtc4eu.website.common.error.ConflictException;
import eu.dtc4eu.website.common.error.NotFoundException;
import eu.dtc4eu.website.common.web.SlugUtil;
import eu.dtc4eu.website.page.dto.PageDtos;
import eu.dtc4eu.website.page.entity.Page;
import eu.dtc4eu.website.page.entity.PageBlock;
import eu.dtc4eu.website.page.repository.PageBlockRepository;
import eu.dtc4eu.website.page.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PageService {

    public static final String DEFAULT_LOCALE = "en";

    private final PageRepository pageRepository;
    private final PageBlockRepository pageBlockRepository;
    private final BlockValidationService blockValidationService;

    @Transactional(readOnly = true)
    public List<PageDtos.PageSummaryDto> list() {
        return pageRepository.findByLocaleOrderByNavOrderAscIdAsc(DEFAULT_LOCALE).stream()
                .map(PageDtos.PageSummaryDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageDtos.PageDetailDto get(Long id) {
        Page page = find(id);
        return PageDtos.PageDetailDto.of(page, pageBlockRepository.findByPageIdOrderBySortOrderAsc(id));
    }

    @Transactional
    public PageDtos.PageDetailDto create(PageDtos.SavePageRequest request, Long actorId) {
        Page page = new Page();
        applyMeta(page, request, actorId, true);
        page = pageRepository.save(page);
        return PageDtos.PageDetailDto.of(page, List.of());
    }

    @Transactional
    public PageDtos.PageDetailDto update(Long id, PageDtos.SavePageRequest request, Long actorId) {
        Page page = find(id);
        applyMeta(page, request, actorId, !page.getSlug().equals(SlugUtil.normalize(request.slug())));
        return PageDtos.PageDetailDto.of(page, pageBlockRepository.findByPageIdOrderBySortOrderAsc(id));
    }

    /** Replaces the page's ordered block list atomically — the builder's save model. */
    @Transactional
    public PageDtos.PageDetailDto saveBlocks(Long id, PageDtos.SaveBlocksRequest request, Long actorId) {
        Page page = find(id);
        List<PageBlock> replacement = new ArrayList<>();
        for (int i = 0; i < request.blocks().size(); i++) {
            PageDtos.SaveBlockRequest blockRequest = request.blocks().get(i);
            Map<String, Object> normalized =
                    blockValidationService.validate(blockRequest.blockType(), blockRequest.payload());
            PageBlock block = new PageBlock();
            block.setPageId(page.getId());
            block.setSortOrder(i);
            block.setBlockType(blockRequest.blockType());
            block.setPayload(normalized);
            replacement.add(block);
        }
        pageBlockRepository.deleteByPageId(page.getId());
        pageBlockRepository.saveAll(replacement);
        page.setUpdatedBy(actorId);
        return PageDtos.PageDetailDto.of(page, replacement);
    }

    @Transactional
    public PageDtos.PageDetailDto setStatus(Long id, Page.Status status, Long actorId) {
        Page page = find(id);
        page.setStatus(status);
        page.setUpdatedBy(actorId);
        return PageDtos.PageDetailDto.of(page, pageBlockRepository.findByPageIdOrderBySortOrderAsc(id));
    }

    @Transactional
    public void delete(Long id) {
        pageRepository.delete(find(id));
    }

    private Page find(Long id) {
        return pageRepository.findById(id).orElseThrow(() -> NotFoundException.of("Page", id));
    }

    private void applyMeta(Page page, PageDtos.SavePageRequest request, Long actorId, boolean slugChanged) {
        String slug = SlugUtil.normalize(request.slug());
        if (slugChanged && pageRepository.existsBySlugAndLocale(slug, DEFAULT_LOCALE)) {
            throw new ConflictException("A page with slug '" + slug + "' already exists");
        }
        page.setSlug(slug);
        page.setLocale(DEFAULT_LOCALE);
        page.setTitle(request.title());
        page.setShowInNav(request.showInNav());
        page.setNavLabel(request.navLabel());
        page.setNavOrder(request.navOrder());
        page.setSeoTitle(request.seoTitle());
        page.setSeoDescription(request.seoDescription());
        page.setUpdatedBy(actorId);
    }
}
