package eu.dtc4eu.website.adminuser.controller;

import eu.dtc4eu.website.adminuser.dto.AdminUserDtos;
import eu.dtc4eu.website.adminuser.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "List CMS users")
    @GetMapping
    public List<AdminUserDtos.AdminUserDto> list() {
        return adminUserService.list();
    }

    @Operation(summary = "Create a CMS user")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDtos.AdminUserDto create(@Valid @RequestBody AdminUserDtos.CreateRequest request) {
        return adminUserService.create(request);
    }

    @Operation(summary = "Update a CMS user (role, name, active, optional password reset)")
    @PutMapping("/{id}")
    public AdminUserDtos.AdminUserDto update(@PathVariable Long id,
                                             @Valid @RequestBody AdminUserDtos.UpdateRequest request) {
        return adminUserService.update(id, request);
    }
}
