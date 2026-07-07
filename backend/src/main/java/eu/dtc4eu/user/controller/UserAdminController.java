package eu.dtc4eu.user.controller;

import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.user.dto.UserDtos.CreateUserRequest;
import eu.dtc4eu.user.dto.UserDtos.UpdateUserRequest;
import eu.dtc4eu.user.dto.UserDtos.UserDetailDto;
import eu.dtc4eu.user.dto.UserDtos.UserDto;
import eu.dtc4eu.user.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

/** User administration (T1). Admin-only, incl. role and data-scope assignment. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Users (admin)")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "List users (filters: q on username/name/email, role, active)")
    @GetMapping
    public PageResponse<UserDto> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String role,
                                      @RequestParam(required = false) Boolean active,
                                      @ParameterObject @PageableDefault(size = 20, sort = "username",
                                              direction = Sort.Direction.ASC) Pageable pageable) {
        return userAdminService.list(q, role, active, pageable);
    }

    /** Lightweight picker for terna building — coordinators may call it too (method overrides class rule). */
    @Operation(summary = "Active users holding the COORDINATOR role (id + name only)")
    @GetMapping("/coordinators")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','COORDINATOR')")
    public java.util.List<CoordinatorOption> coordinators() {
        return userAdminService.coordinatorOptions();
    }

    public record CoordinatorOption(Long id, String username, String fullName) {
    }

    @GetMapping("/{id}")
    public UserDetailDto get(@PathVariable Long id) {
        return userAdminService.get(id);
    }

    @Operation(summary = "Create a user with roles and facility/terna scope")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetailDto create(@Valid @RequestBody CreateUserRequest request,
                                @AuthenticationPrincipal AppUserDetails actor) {
        return userAdminService.create(request, actor);
    }

    @Operation(summary = "Update profile, roles, scope, active flag; password only when provided")
    @PutMapping("/{id}")
    public UserDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                                @AuthenticationPrincipal AppUserDetails actor) {
        return userAdminService.update(id, request, actor);
    }
}
