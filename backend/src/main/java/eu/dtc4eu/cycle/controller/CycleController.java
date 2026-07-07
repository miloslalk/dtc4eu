package eu.dtc4eu.cycle.controller;

import eu.dtc4eu.cycle.dto.CycleDtos.CreateCycleRequest;
import eu.dtc4eu.cycle.dto.CycleDtos.CycleDto;
import eu.dtc4eu.cycle.dto.CycleDtos.UpdateCycleRequest;
import eu.dtc4eu.cycle.service.CycleService;
import eu.dtc4eu.security.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/cycles")
@RequiredArgsConstructor
@Tag(name = "Annual cycles")
public class CycleController {

    private final CycleService cycleService;

    @Operation(summary = "All cycles (any authenticated user; feeds the cycle selector)")
    @GetMapping
    public List<CycleDto> list() {
        return cycleService.list();
    }

    @GetMapping("/{id}")
    public CycleDto get(@PathVariable Long id) {
        return cycleService.get(id);
    }

    @Operation(summary = "Create a cycle (admin)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CycleDto create(@Valid @RequestBody CreateCycleRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return cycleService.create(request, actor);
    }

    @Operation(summary = "Update dates or status (admin); closed/archived cycles become read-only")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CycleDto update(@PathVariable Long id, @Valid @RequestBody UpdateCycleRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return cycleService.update(id, request, actor);
    }

    @Operation(summary = "Make this the platform-wide current cycle (admin)")
    @PostMapping("/{id}/set-current")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public CycleDto setCurrent(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails actor) {
        return cycleService.setCurrent(id, actor);
    }
}
