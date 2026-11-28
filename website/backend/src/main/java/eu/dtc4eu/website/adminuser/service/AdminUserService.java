package eu.dtc4eu.website.adminuser.service;

import eu.dtc4eu.website.adminuser.dto.AdminUserDtos;
import eu.dtc4eu.website.adminuser.entity.AdminUser;
import eu.dtc4eu.website.adminuser.repository.AdminUserRepository;
import eu.dtc4eu.website.common.error.BadRequestException;
import eu.dtc4eu.website.common.error.ConflictException;
import eu.dtc4eu.website.common.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<AdminUserDtos.AdminUserDto> list() {
        return adminUserRepository.findAll().stream()
                .map(AdminUserDtos.AdminUserDto::of)
                .toList();
    }

    @Transactional
    public AdminUserDtos.AdminUserDto create(AdminUserDtos.CreateRequest request) {
        if (adminUserRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists");
        }
        AdminUser user = new AdminUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role());
        return AdminUserDtos.AdminUserDto.of(adminUserRepository.save(user));
    }

    @Transactional
    public AdminUserDtos.AdminUserDto update(Long id, AdminUserDtos.UpdateRequest request) {
        AdminUser user = adminUserRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Admin user", id));
        boolean losesAdmin = user.getRole() == AdminUser.Role.ADMIN
                && (request.role() != AdminUser.Role.ADMIN || !request.active());
        if (losesAdmin && adminUserRepository.countByRoleAndActiveTrue(AdminUser.Role.ADMIN) <= 1) {
            throw new BadRequestException("Cannot remove the last active ADMIN");
        }
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            // every session on the old password ends when its short-lived access token expires
            user.setTokensValidAfter(java.time.OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        }
        return AdminUserDtos.AdminUserDto.of(user);
    }
}
