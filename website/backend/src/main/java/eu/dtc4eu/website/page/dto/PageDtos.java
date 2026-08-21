package eu.dtc4eu.website.page.dto;

import eu.dtc4eu.website.page.entity.BlockType;
import eu.dtc4eu.website.page.entity.Page;
import eu.dtc4eu.website.page.entity.PageBlock;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class PageDtos {

    private PageDtos() {
    }

    public record BlockDto(Long id, BlockType blockType, Map<String, Object> payload) {

        public static BlockDto of(PageBlock block) {
            return new BlockDto(block.getId(), block.getBlockType(), block.getPayload());
        }
    }

    public record PageSummaryDto(Long id, String slug, String locale, String title, Page.Status status,
                                 boolean showInNav, String navLabel, int navOrder, OffsetDateTime updatedAt) {

        public static PageSummaryDto of(Page page) {
            return new PageSummaryDto(page.getId(), page.getSlug(), page.getLocale(), page.getTitle(),
                    page.getStatus(), page.isShowInNav(), page.getNavLabel(), page.getNavOrder(),
                    page.getUpdatedAt());
        }
    }

    public record PageDetailDto(Long id, String slug, String locale, String title, Page.Status status,
                                boolean showInNav, String navLabel, int navOrder,
                                String seoTitle, String seoDescription, OffsetDateTime updatedAt,
                                List<BlockDto> blocks) {

        public static PageDetailDto of(Page page, List<PageBlock> blocks) {
            return new PageDetailDto(page.getId(), page.getSlug(), page.getLocale(), page.getTitle(),
                    page.getStatus(), page.isShowInNav(), page.getNavLabel(), page.getNavOrder(),
                    page.getSeoTitle(), page.getSeoDescription(), page.getUpdatedAt(),
                    blocks.stream().map(BlockDto::of).toList());
        }
    }

    public record SavePageRequest(
            @NotBlank @Size(max = 120) String slug,
            @NotBlank @Size(max = 200) String title,
            @NotNull Boolean showInNav,
            @Size(max = 60) String navLabel,
            @NotNull Integer navOrder,
            @Size(max = 200) String seoTitle,
            @Size(max = 320) String seoDescription) {
    }

    public record SaveBlockRequest(@NotNull BlockType blockType, Map<String, Object> payload) {
    }

    public record SaveBlocksRequest(@NotNull @Valid List<SaveBlockRequest> blocks) {
    }
}
