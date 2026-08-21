package eu.dtc4eu.website.security;

import eu.dtc4eu.website.adminuser.entity.AdminUser;
import eu.dtc4eu.website.adminuser.repository.AdminUserRepository;
import eu.dtc4eu.website.security.dto.AuthDtos;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        AdminUser user = adminUserRepository.findByUsername(request.username())
                .filter(AdminUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return new AuthDtos.TokenResponse(jwtService.createAccessToken(user), jwtService.createRefreshToken(user));
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtService.parse(request.refreshToken());
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        AdminUser user = adminUserRepository.findByUsername(claims.getSubject())
                .filter(AdminUser::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return new AuthDtos.TokenResponse(jwtService.createAccessToken(user), jwtService.createRefreshToken(user));
    }
}
