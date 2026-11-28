package eu.dtc4eu.security;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.cycle.repository.AnnualCycleRepository;
import eu.dtc4eu.security.dto.AuthDtos.CycleInfo;
import eu.dtc4eu.security.dto.AuthDtos.LoginRequest;
import eu.dtc4eu.security.dto.AuthDtos.MeResponse;
import eu.dtc4eu.security.dto.AuthDtos.ScopeInfo;
import eu.dtc4eu.security.dto.AuthDtos.TokenResponse;
import eu.dtc4eu.security.dto.AuthDtos.UserInfo;
import eu.dtc4eu.user.entity.User;
import eu.dtc4eu.user.repository.UserFacilityAccessRepository;
import eu.dtc4eu.user.repository.UserRepository;
import eu.dtc4eu.user.repository.UserTernaAccessRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserFacilityAccessRepository facilityAccessRepository;
    private final UserTernaAccessRepository ternaAccessRepository;
    private final AnnualCycleRepository cycleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Transactional(readOnly = true, noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameWithRoles(request.username()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.failure(user != null ? user.getId() : null, "LOGIN", "user", null,
                    Map.of("username", request.username()));
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.isActive()) {
            auditService.failure(user.getId(), "LOGIN", "user", user.getId(), Map.of("reason", "inactive"));
            throw new DisabledException("Account disabled");
        }
        auditService.success(user.getId(), "LOGIN", "user", user.getId());
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        User user = userRepository.findByUsernameWithRoles(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (!user.isActive()) {
            throw new DisabledException("Account disabled");
        }
        if (!TokenCutoff.accepts(user.getTokensValidAfter(), claims.getIssuedAt())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        return tokensFor(user);
    }

    public void logout(AppUserDetails principal) {
        auditService.success(principal.getId(), "LOGOUT", "user", principal.getId());
    }

    @Transactional(readOnly = true)
    public MeResponse me(AppUserDetails principal) {
        User user = userRepository.findByUsernameWithRoles(principal.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        ScopeInfo scope = new ScopeInfo(
                facilityAccessRepository.findFacilityIdsByUserId(user.getId()),
                ternaAccessRepository.findTernaIdsByUserId(user.getId()));
        CycleInfo defaultCycle = cycleRepository
                .findFirstByStatusOrderByStartDateDescIdDesc(eu.dtc4eu.cycle.entity.AnnualCycle.Status.OPEN)
                .map(c -> new CycleInfo(c.getId(), c.getCode(), c.getStatus().name(), c.getSequenceNumber()))
                .orElse(null);
        return new MeResponse(userInfo(user), scope, defaultCycle);
    }

    private TokenResponse tokensFor(User user) {
        return new TokenResponse(jwtService.createAccessToken(user), jwtService.createRefreshToken(user),
                userInfo(user));
    }

    /** mustChangePassword is filled in by AuthController (PasswordChangeGate) to keep this class's user-slice edges frozen. */
    private UserInfo userInfo(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getPreferredLang(), user.roleCodes(), false);
    }
}
