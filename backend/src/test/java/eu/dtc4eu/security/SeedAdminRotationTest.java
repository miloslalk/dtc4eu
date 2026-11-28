package eu.dtc4eu.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeedAdminRotationTest {

    private final PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);

    @Test
    void startUpFlagsThenReplacesTheSeedPassword() {
        when(passwordPolicyService.replaceSeedAdminPasswordIfUnrotated("Bootstrap-pass-2026")).thenReturn(true);
        new SeedAdminRotation(passwordPolicyService, "Bootstrap-pass-2026").run(null);

        var order = inOrder(passwordPolicyService);
        order.verify(passwordPolicyService).flagSeedAdminIfUnrotated();
        order.verify(passwordPolicyService).replaceSeedAdminPasswordIfUnrotated("Bootstrap-pass-2026");
    }

    @Test
    void startUpFailsWhenTheSeedPasswordCannotBeReplaced() {
        when(passwordPolicyService.replaceSeedAdminPasswordIfUnrotated(""))
                .thenThrow(new IllegalStateException("set SEED_ADMIN_PASSWORD"));
        assertThatThrownBy(() -> new SeedAdminRotation(passwordPolicyService, "").run(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
