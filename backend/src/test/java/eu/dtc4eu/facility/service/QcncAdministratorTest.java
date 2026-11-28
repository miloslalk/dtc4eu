package eu.dtc4eu.facility.service;

import eu.dtc4eu.facility.entity.QcncAssessment.AdministrationType;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.user.entity.Role;
import eu.dtc4eu.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Each QCNC administration is entered by its own administrator (register §4); admins may enter any. */
class QcncAdministratorTest {

    @Test
    void theFacilitatorEntersOnlyTheSelfAssessment() {
        AppUserDetails facilitator = principalWith(Role.INTERNAL_FACILITATOR);
        assertThatCode(() -> QcncStandardsService.requireAdministrator(AdministrationType.INTERNAL_FACILITATOR,
                facilitator)).doesNotThrowAnyException();
        for (AdministrationType external : new AdministrationType[] {
                AdministrationType.TERNA_COORDINATOR_OWN, AdministrationType.TERNA_COORDINATOR_DELEGATION}) {
            assertThatThrownBy(() -> QcncStandardsService.requireAdministrator(external, facilitator))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void coordinatorsEnterOnlyTheExternalAdministrations() {
        for (String role : new String[] {Role.COORDINATOR, Role.AUDIT_COORDINATOR}) {
            AppUserDetails coordinator = principalWith(role);
            assertThatCode(() -> QcncStandardsService.requireAdministrator(AdministrationType.TERNA_COORDINATOR_OWN,
                    coordinator)).doesNotThrowAnyException();
            assertThatCode(() -> QcncStandardsService.requireAdministrator(
                    AdministrationType.TERNA_COORDINATOR_DELEGATION, coordinator)).doesNotThrowAnyException();
            assertThatThrownBy(() -> QcncStandardsService.requireAdministrator(
                    AdministrationType.INTERNAL_FACILITATOR, coordinator))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void adminsEnterAnyAdministration() {
        for (String role : new String[] {Role.ADMIN, Role.SUPER_ADMIN}) {
            for (AdministrationType type : AdministrationType.values()) {
                assertThatCode(() -> QcncStandardsService.requireAdministrator(type, principalWith(role)))
                        .doesNotThrowAnyException();
            }
        }
    }

    private AppUserDetails principalWith(String roleCode) {
        User user = new User();
        user.setUsername("u-" + roleCode);
        user.setPasswordHash("x");
        Role role = new Role();
        role.setCode(roleCode);
        role.setName(roleCode);
        user.getRoles().add(role);
        return new AppUserDetails(user);
    }
}
