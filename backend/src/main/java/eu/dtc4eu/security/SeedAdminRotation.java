package eu.dtc4eu.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * FIX-LIST X4: on production start-up, if the seeded {@code admin} still accepts
 * the published seed password, swap it for the private bootstrap password
 * ({@code SEED_ADMIN_PASSWORD}) and require a change at its next login; without
 * one, start-up fails. Enabled by {@code dtc4eu.security.force-seed-admin-rotation}
 * (application-prod.yml); dev and tests keep the seed so local logins and ITs work.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "dtc4eu.security.force-seed-admin-rotation", havingValue = "true")
public class SeedAdminRotation implements ApplicationRunner {

    private final PasswordPolicyService passwordPolicyService;
    private final String bootstrapPassword;

    public SeedAdminRotation(PasswordPolicyService passwordPolicyService,
                             @Value("${dtc4eu.security.seed-admin-password:}") String bootstrapPassword) {
        this.passwordPolicyService = passwordPolicyService;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        passwordPolicyService.flagSeedAdminIfUnrotated();
        if (passwordPolicyService.replaceSeedAdminPasswordIfUnrotated(bootstrapPassword)) {
            log.warn("The seeded admin's published password was replaced by SEED_ADMIN_PASSWORD: "
                    + "a change is required at next login");
        }
    }
}
