package eu.dtc4eu.security;

import eu.dtc4eu.config.AppProperties;
import eu.dtc4eu.user.entity.Role;
import eu.dtc4eu.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-with-enough-length-0123456789";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = jwtServiceWith(Duration.ofMinutes(15), Duration.ofDays(7));
        user = new User();
        user.setUsername("admin");
        Role role = new Role();
        role.setCode(Role.SUPER_ADMIN);
        role.setName("Super Administrator");
        user.getRoles().add(role);
    }

    @Test
    void accessTokenRoundTripCarriesSubjectRolesAndType() {
        String token = jwtService.createAccessToken(user);

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(jwtService.isAccessToken(claims)).isTrue();
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
        assertThat(claims.get(JwtService.CLAIM_ROLES, List.class)).containsExactly("SUPER_ADMIN");
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String token = jwtService.createRefreshToken(user);

        Claims claims = jwtService.parse(token);

        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiringService = jwtServiceWith(Duration.ofMillis(-1000), Duration.ofDays(7));
        String token = expiringService.createAccessToken(user);

        assertThatThrownBy(() -> expiringService.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.createAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.parse(tampered)).isInstanceOf(JwtException.class);
    }

    private JwtService jwtServiceWith(Duration accessTtl, Duration refreshTtl) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(SECRET, accessTtl, refreshTtl), null, null);
        return new JwtService(properties);
    }
}
