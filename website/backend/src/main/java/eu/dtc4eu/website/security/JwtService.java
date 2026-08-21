package eu.dtc4eu.website.security;

import eu.dtc4eu.website.adminuser.entity.AdminUser;
import eu.dtc4eu.website.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and parses the two token kinds. Refresh tokens are stateless signed
 * JWTs; they carry typ=refresh and a longer TTL, and are accepted only by the
 * refresh endpoint. Same model as the platform, but signed with this app's
 * own secret — platform tokens are never valid here.
 */
@Service
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final AppProperties.Jwt jwtProperties;

    public JwtService(AppProperties appProperties) {
        this.jwtProperties = appProperties.jwt();
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(AdminUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.accessTtl())))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(AdminUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.refreshTtl())))
                .signWith(key)
                .compact();
    }

    /** Parses and verifies signature + expiry; throws io.jsonwebtoken.JwtException when invalid. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }
}
