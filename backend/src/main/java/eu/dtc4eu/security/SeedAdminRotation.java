package eu.dtc4eu.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * FIX-LIST X4: on production start-up, if the seeded {@code admin} still accepts
 * the published seed password, require a change at its next login. Enabled by
 * {@code dtc4eu.security.force-seed-admin-rotation} (application-prod.yml); dev
 * and tests keep the seed so local logins and ITs work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dtc4eu.security.force-seed-admin-rotation", havingValue = "true")
public class SeedAdminRotation implements ApplicationRunner {

    private final PasswordPolicyService passwordPolicyService;

    @Override
    public void run(ApplicationArguments args) {
        if (passwordPolicyService.flagSeedAdminIfUnrotated()) {
            log.warn("The seeded admin still uses the published password: a change is required at next login");
        }
    }
}
