package eu.dtc4eu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dtc4eu.common.error.ApiError;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the Bearer access token into an authenticated principal. The user
 * is reloaded from the DB on each request so deactivation and role changes
 * take effect immediately.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final java.util.Set<String> ALLOWED_DURING_PASSWORD_CHANGE = java.util.Set.of(
            "/api/v1/auth/me", "/api/v1/auth/me/password", "/api/v1/auth/logout");

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final PasswordChangeGate passwordChangeGate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()));
                if (jwtService.isAccessToken(claims)) {
                    AppUserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());
                    if (user.isEnabled() && passwordChangeBlocks(request, user)) {
                        writePasswordChangeRequired(request, response);
                        return;
                    }
                    if (user.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | UsernameNotFoundException ignored) {
                // request proceeds unauthenticated; the entry point produces the 401
            }
        }
        chain.doFilter(request, response);
    }

    /** Until a required change is made (X4), only reading oneself, changing the password and logging out work. */
    private boolean passwordChangeBlocks(HttpServletRequest request, AppUserDetails user) {
        String path = request.getRequestURI();
        if (ALLOWED_DURING_PASSWORD_CHANGE.contains(path)) {
            return false;
        }
        return passwordChangeGate.mustChangePassword(user.getId());
    }

    private void writePasswordChangeRequired(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(HttpServletResponse.SC_FORBIDDEN,
                "Forbidden", "Password change required", request.getRequestURI()));
    }
}
