package eu.dtc4eu.website.security;

import eu.dtc4eu.website.adminuser.entity.AdminUser;
import eu.dtc4eu.website.adminuser.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedAdminRotationTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AdminUserRepository repository = mock(AdminUserRepository.class);

    private AdminUser seededWith(String password) {
        AdminUser user = new AdminUser();
        user.setUsername(SeedAdminRotation.SEED_USERNAME);
        user.setPasswordHash(encoder.encode(password));
        when(repository.findByUsername(SeedAdminRotation.SEED_USERNAME)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void thePublishedPasswordIsReplacedByTheBootstrapOne() {
        AdminUser user = seededWith(SeedAdminRotation.SEED_PASSWORD);
        assertThat(new SeedAdminRotation(repository, encoder, "Bootstrap-pass-2026").replaceIfUnrotated()).isTrue();
        assertThat(encoder.matches("Bootstrap-pass-2026", user.getPasswordHash())).isTrue();
        assertThat(encoder.matches(SeedAdminRotation.SEED_PASSWORD, user.getPasswordHash())).isFalse();
        verify(repository).save(user);
    }

    @Test
    void startUpRefusesWithoutABootstrapPassword() {
        seededWith(SeedAdminRotation.SEED_PASSWORD);
        for (String missing : new String[] {"", "  ", SeedAdminRotation.SEED_PASSWORD}) {
            assertThatThrownBy(() -> new SeedAdminRotation(repository, encoder, missing).replaceIfUnrotated())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SEED_ADMIN_PASSWORD");
        }
    }

    @Test
    void aRotatedPasswordIsLeftAlone() {
        AdminUser user = seededWith("Someone-chose-this-1");
        assertThat(new SeedAdminRotation(repository, encoder, "").replaceIfUnrotated()).isFalse();
        assertThat(encoder.matches("Someone-chose-this-1", user.getPasswordHash())).isTrue();
        verify(repository, never()).save(user);
    }
}
