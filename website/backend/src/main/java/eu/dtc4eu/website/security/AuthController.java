package eu.dtc4eu.website.security;

import eu.dtc4eu.website.security.dto.AuthDtos;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginThrottle loginThrottle;

    @Operation(summary = "CMS sign-in (429 after repeated failures)")
    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest http) {
        String address = http.getRemoteAddr();
        loginThrottle.check(request.username(), address);
        try {
            AuthDtos.TokenResponse tokens = authService.login(request);
            loginThrottle.recordSuccess(request.username(), address);
            return tokens;
        } catch (AuthenticationException e) {
            loginThrottle.recordFailure(request.username(), address);
            throw e;
        }
    }

    @Operation(summary = "Exchange a refresh token for a new token pair")
    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "Current CMS user")
    @GetMapping("/me")
    public AuthDtos.MeResponse me(@AuthenticationPrincipal AdminUserDetails principal) {
        return AuthDtos.MeResponse.of(principal.getUser());
    }
}
