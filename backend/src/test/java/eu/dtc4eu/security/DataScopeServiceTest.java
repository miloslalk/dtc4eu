package eu.dtc4eu.security;

import eu.dtc4eu.user.entity.Role;
import eu.dtc4eu.user.entity.User;
import eu.dtc4eu.user.repository.UserFacilityAccessRepository;
import eu.dtc4eu.user.repository.UserTernaAccessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {

    @Mock
    private UserFacilityAccessRepository facilityAccessRepository;

    @Mock
    private UserTernaAccessRepository ternaAccessRepository;

    @InjectMocks
    private DataScopeService dataScopeService;

    @Test
    void adminSeesEverything() {
        DataScope scope = dataScopeService.scopeOf(principalWith(1L, Role.ADMIN));

        assertThat(scope.all()).isTrue();
        assertThat(scope.canSeeFacility(42L)).isTrue();
        assertThat(scope.canSeeTerna(7L)).isTrue();
    }

    @Test
    void researchTeamSeesEverything() {
        assertThat(dataScopeService.scopeOf(principalWith(1L, Role.RESEARCH_TEAM)).all()).isTrue();
    }

    @Test
    void internalFacilitatorSeesOnlyAssignedFacilities() {
        when(facilityAccessRepository.findFacilityIdsByUserId(5L)).thenReturn(List.of(10L, 11L));

        DataScope scope = dataScopeService.scopeOf(principalWith(5L, Role.INTERNAL_FACILITATOR));

        assertThat(scope.all()).isFalse();
        assertThat(scope.canSeeFacility(10L)).isTrue();
        assertThat(scope.canSeeFacility(99L)).isFalse();
        assertThat(scope.respondentOnly()).isFalse();
    }

    @Test
    void coordinatorSeesAssignedTernasAndTheirFacilities() {
        when(ternaAccessRepository.findTernaIdsByUserId(6L)).thenReturn(List.of(3L));
        when(ternaAccessRepository.findCoordinatorFacilityIds(6L)).thenReturn(List.of(20L, 21L, 22L));

        DataScope scope = dataScopeService.scopeOf(principalWith(6L, Role.COORDINATOR));

        assertThat(scope.canSeeTerna(3L)).isTrue();
        assertThat(scope.canSeeTerna(4L)).isFalse();
        assertThat(scope.canSeeFacility(21L)).isTrue();
        assertThat(scope.canSeeFacility(10L)).isFalse();
    }

    @Test
    void respondentWithNoAssignmentsIsRespondentOnly() {
        DataScope scope = dataScopeService.scopeOf(principalWith(9L, Role.RESPONDENT));

        assertThat(scope.all()).isFalse();
        assertThat(scope.respondentOnly()).isTrue();
        assertThat(scope.canSeeFacility(anyFacility())).isFalse();
    }

    private AppUserDetails principalWith(Long id, String roleCode) {
        User user = new User();
        user.setUsername("u" + id);
        user.setPasswordHash("x");
        Role role = new Role();
        role.setCode(roleCode);
        role.setName(roleCode);
        user.getRoles().add(role);
        AppUserDetails details = new AppUserDetails(user) {
            @Override
            public Long getId() {
                return id;
            }
        };
        return details;
    }

    private long anyFacility() {
        return 123L;
    }
}
