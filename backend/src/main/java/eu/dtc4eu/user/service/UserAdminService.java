package eu.dtc4eu.user.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.user.dto.UserDtos.CreateUserRequest;
import eu.dtc4eu.user.dto.UserDtos.UpdateUserRequest;
import eu.dtc4eu.user.dto.UserDtos.UserDetailDto;
import eu.dtc4eu.user.dto.UserDtos.UserDto;
import eu.dtc4eu.user.entity.Role;
import eu.dtc4eu.user.entity.User;
import eu.dtc4eu.user.entity.UserFacilityAccess;
import eu.dtc4eu.user.entity.UserTernaAccess;
import eu.dtc4eu.user.repository.RoleRepository;
import eu.dtc4eu.user.repository.UserFacilityAccessRepository;
import eu.dtc4eu.user.repository.UserRepository;
import eu.dtc4eu.user.repository.UserTernaAccessRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserFacilityAccessRepository facilityAccessRepository;
    private final UserTernaAccessRepository ternaAccessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String q, String role, Boolean active, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.join("roles").get("code"), role));
                query.distinct(true);
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> page = userRepository.findAll(spec, pageable);
        return PageResponse.of(page, UserDto::of);
    }

    @Transactional(readOnly = true)
    public UserDetailDto get(Long id) {
        return detail(find(id));
    }

    @Transactional(readOnly = true)
    public List<eu.dtc4eu.user.controller.UserAdminController.CoordinatorOption> coordinatorOptions() {
        return userRepository.findActiveByRoleCode(Role.COORDINATOR).stream()
                .map(u -> new eu.dtc4eu.user.controller.UserAdminController.CoordinatorOption(
                        u.getId(), u.getUsername(), u.getFullName()))
                .toList();
    }

    @Transactional
    public UserDetailDto create(CreateUserRequest request, AppUserDetails actor) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken");
        }
        if (request.email() != null && !request.email().isBlank() && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(blankToNull(request.email()));
        user.setFullName(request.fullName());
        user.setPreferredLang(request.preferredLang() != null ? request.preferredLang() : "en");
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(resolveRoles(request.roles()));
        user = userRepository.save(user);
        replaceScope(user.getId(), request.facilityIds(), request.ternaIds());
        auditService.success(actor.getId(), "CREATE", "user", user.getId());
        return detail(user);
    }

    @Transactional
    public UserDetailDto update(Long id, UpdateUserRequest request, AppUserDetails actor) {
        User user = find(id);
        if (request.email() != null && !request.email().isBlank()
                && !request.email().equals(user.getEmail()) && userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }
        if (Boolean.FALSE.equals(request.active()) && user.getId().equals(actor.getId())) {
            throw new BadRequestException("You cannot deactivate your own account");
        }
        user.setEmail(blankToNull(request.email()));
        user.setFullName(request.fullName());
        if (request.preferredLang() != null) {
            user.setPreferredLang(request.preferredLang());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setRoles(resolveRoles(request.roles()));
        replaceScope(user.getId(), request.facilityIds(), request.ternaIds());
        auditService.success(actor.getId(), "UPDATE", "user", user.getId());
        return detail(user);
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        Set<Role> roles = new HashSet<>();
        for (String code : codes) {
            roles.add(roleRepository.findByCode(code)
                    .orElseThrow(() -> new BadRequestException("Unknown role: " + code)));
        }
        return roles;
    }

    private void replaceScope(Long userId, List<Long> facilityIds, List<Long> ternaIds) {
        facilityAccessRepository.deleteByUserId(userId);
        ternaAccessRepository.deleteByUserId(userId);
        if (facilityIds != null) {
            facilityAccessRepository.saveAll(facilityIds.stream()
                    .map(fid -> new UserFacilityAccess(userId, fid)).toList());
        }
        if (ternaIds != null) {
            ternaAccessRepository.saveAll(ternaIds.stream()
                    .map(tid -> new UserTernaAccess(userId, tid)).toList());
        }
    }

    private UserDetailDto detail(User user) {
        return new UserDetailDto(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getPreferredLang(), user.isActive(), user.roleCodes(),
                facilityAccessRepository.findFacilityIdsByUserId(user.getId()),
                ternaAccessRepository.findTernaIdsByUserId(user.getId()),
                user.getCreatedAt());
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
