package eu.dtc4eu.website.security;

import eu.dtc4eu.website.adminuser.repository.AdminUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On production start-up, if the seeded {@code webadmin} still accepts the
 * password published in V2 and DEPLOY.md, swap it for the private bootstrap
 * password ({@code SEED_ADMIN_PASSWORD}); without one, start-up fails, so the
 * CMS is never reachable with a password anyone can read in the repository.
 * Enabled by {@code website.security.force-seed-admin-rotation} (application-prod.yml).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "website.security.force-seed-admin-rotation", havingValue = "true")
public class SeedAdminRotation implements ApplicationRunner {

    static final String SEED_USERNAME = "webadmin";
    static final String SEED_PASSWORD = "Dtc4eu!Web2026";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapPassword;

    public SeedAdminRotation(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
                             @Value("${website.security.seed-admin-password:}") String bootstrapPassword) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (replaceIfUnrotated()) {
            log.warn("The seeded webadmin's published password was replaced by SEED_ADMIN_PASSWORD: "
                    + "change it from the CMS Users screen");
        }
    }

    /** True if the published password still worked and was replaced; throws when no bootstrap password is set. */
    boolean replaceIfUnrotated() {
        return adminUserRepository.findByUsername(SEED_USERNAME)
                .filter(user -> passwordEncoder.matches(SEED_PASSWORD, user.getPasswordHash()))
                .map(user -> {
                    if (bootstrapPassword == null || bootstrapPassword.isBlank()
                            || bootstrapPassword.equals(SEED_PASSWORD)) {
                        throw new IllegalStateException("The seeded webadmin still uses the published password: "
                                + "set SEED_ADMIN_PASSWORD to a private bootstrap password (see deploy/DEPLOY.md)");
                    }
                    user.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
                    adminUserRepository.save(user);
                    return true;
                })
                .orElse(false);
    }
}
