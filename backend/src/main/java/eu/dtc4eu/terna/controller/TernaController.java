package eu.dtc4eu.terna.controller;

import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.terna.dto.TernaDtos.SaveTernaRequest;
import eu.dtc4eu.terna.dto.TernaDtos.TernaDto;
import eu.dtc4eu.terna.dto.TernaDtos.TernaRowDto;
import eu.dtc4eu.terna.entity.Terna;
import eu.dtc4eu.terna.service.TernaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Module 2 — Ternas (spec T3). Cycle-scoped; coordinators see only assigned ternas. */
@RestController
@RequestMapping("/api/v1/ternas")
@RequiredArgsConstructor
@Tag(name = "Ternas (Module 2)")
public class TernaController {

    private final TernaService ternaService;

    @Operation(summary = "List ternas in the selected cycle (filters: type, facility, founding-date range)")
    @GetMapping
    public PageResponse<TernaRowDto> list(@RequestParam(required = false) Terna.TernaType type,
                                          @RequestParam(required = false) Long facilityId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                          @ParameterObject @PageableDefault(size = 20, sort = "ternaCode",
                                                  direction = Sort.Direction.ASC) Pageable pageable,
                                          @AuthenticationPrincipal AppUserDetails principal) {
        return ternaService.list(type, facilityId, dateFrom, dateTo, pageable, principal);
    }

    @GetMapping("/{id}")
    public TernaDto get(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails principal) {
        return ternaService.get(id, principal);
    }

    @Operation(summary = "Build a terna: 3 facilities (facilitators auto-pulled), coordinators, 5 dates")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COORDINATOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public TernaDto create(@Valid @RequestBody SaveTernaRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return ternaService.create(request, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COORDINATOR')")
    public TernaDto update(@PathVariable Long id, @Valid @RequestBody SaveTernaRequest request,
                           @AuthenticationPrincipal AppUserDetails actor) {
        return ternaService.update(id, request, actor);
    }
}
