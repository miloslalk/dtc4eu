package eu.dtc4eu.progress.controller;

import eu.dtc4eu.progress.dto.ProgressDtos.FacilityProgressDto;
import eu.dtc4eu.progress.service.ProgressService;
import eu.dtc4eu.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Instrument completion per facility for the selected cycle (dashboard task list, terna matrix). */
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
@Tag(name = "Progress")
public class ProgressController {

    private final ProgressService progressService;

    @Operation(summary = "Completion of the instruments of the caller's facilities in the selected cycle")
    @GetMapping("/me")
    public List<FacilityProgressDto> mine(@AuthenticationPrincipal AppUserDetails principal) {
        return progressService.mine(principal);
    }

    @Operation(summary = "Completion matrix of one terna's facilities × instruments")
    @GetMapping("/ternas/{ternaId}")
    public List<FacilityProgressDto> terna(@PathVariable Long ternaId,
                                           @AuthenticationPrincipal AppUserDetails principal) {
        return progressService.terna(ternaId, principal);
    }
}
