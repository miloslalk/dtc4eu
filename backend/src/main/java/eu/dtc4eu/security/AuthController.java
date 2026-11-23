package eu.dtc4eu.security;

import eu.dtc4eu.security.dto.AuthDtos.ChangePasswordRequest;
import eu.dtc4eu.security.dto.AuthDtos.LoginRequest;
import eu.dtc4eu.security.dto.AuthDtos.MeResponse;
import eu.dtc4eu.security.dto.AuthDtos.RefreshRequest;
import eu.dtc4eu.security.dto.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final CyclesWithDataService cyclesWithDataService;
    private final PasswordPolicyService passwordPolicyService;
    private final PasswordChangeGate passwordChangeGate;

    @Operation(summary = "Log in with username + password, returns access and refresh tokens")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return withPasswordFlag(authService.login(request));
    }

    @Operation(summary = "Exchange a valid refresh token for a new token pair")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return withPasswordFlag(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Log out (audit-logged; tokens are stateless and discarded client-side)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AppUserDetails principal) {
        authService.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ids of the cycles holding data in the caller's scope, most recent first")
    @GetMapping("/me/cycles-with-data")
    public List<Long> cyclesWithData(@AuthenticationPrincipal AppUserDetails principal) {
        return cyclesWithDataService.cycleIds(principal);
    }

    @Operation(summary = "Change the caller's own password (clears a required change)")
    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               @AuthenticationPrincipal AppUserDetails principal) {
        passwordPolicyService.changePassword(principal, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current user, roles, data scope and current cycle")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        MeResponse me = authService.me(principal);
        return me.withUser(me.user().withMustChangePassword(passwordChangeGate.mustChangePassword(me.user().id())));
    }

    private TokenResponse withPasswordFlag(TokenResponse tokens) {
        return tokens.withUser(tokens.user().withMustChangePassword(
                passwordChangeGate.mustChangePassword(tokens.user().id())));
    }
}
