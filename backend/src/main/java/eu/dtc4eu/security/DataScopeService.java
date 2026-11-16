package eu.dtc4eu.security;

import eu.dtc4eu.user.entity.Role;
import eu.dtc4eu.user.repository.UserFacilityAccessRepository;
import eu.dtc4eu.user.repository.UserTernaAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Turns the caller's roles + access assignments into a {@link DataScope}
 * (spec §6, amended by v0.2 register §9):
 *  - SUPER_ADMIN / ADMIN / RESEARCH_TEAM / UPPER_COORDINATOR → everything
 *    ("All Cycles, all Countries, all Ternas")
 *  - COORDINATOR → assigned ternas + their member facilities, plus the
 *    admin-assigned facility pool it may build ternas from
 *  - INTERNAL_FACILITATOR / EVALUATOR / AUDIT_COORDINATOR → assigned facilities
 *  - RESPONDENT (only) → own responses; its assigned facilities are the ones
 *    it may answer for ({@code respondentFacilityIds}), not read
 * Multiple roles union their scopes. Out-of-scope reads must surface as 404.
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final UserFacilityAccessRepository facilityAccessRepository;
    private final UserTernaAccessRepository ternaAccessRepository;

    @Transactional(readOnly = true)
    public DataScope scopeOf(AppUserDetails principal) {
        if (principal.hasAnyRole(Role.SUPER_ADMIN, Role.ADMIN, Role.RESEARCH_TEAM, Role.UPPER_COORDINATOR)) {
            return new DataScope(principal.getId(), true, Set.of(), Set.of(), false, Set.of(), false);
        }

        Set<Long> facilityIds = new HashSet<>();
        Set<Long> ternaIds = new HashSet<>();
        boolean respondent = principal.hasRole(Role.RESPONDENT);
        Set<Long> respondentFacilityIds = Set.of();

        if (principal.hasRole(Role.INTERNAL_FACILITATOR) || principal.hasRole(Role.EVALUATOR)
                || principal.hasRole(Role.AUDIT_COORDINATOR) || principal.hasRole(Role.COORDINATOR)) {
            facilityIds.addAll(facilityAccessRepository.findFacilityIdsByUserId(principal.getId()));
        }
        if (principal.hasRole(Role.COORDINATOR)) {
            ternaIds.addAll(ternaAccessRepository.findTernaIdsByUserId(principal.getId()));
            facilityIds.addAll(ternaAccessRepository.findCoordinatorFacilityIds(principal.getId()));
        }
        if (respondent) {
            respondentFacilityIds = Set.copyOf(facilityAccessRepository.findFacilityIdsByUserId(principal.getId()));
        }

        boolean respondentOnly = facilityIds.isEmpty() && ternaIds.isEmpty();
        return new DataScope(principal.getId(), false, Set.copyOf(facilityIds), Set.copyOf(ternaIds),
                respondent, respondentFacilityIds, respondentOnly);
    }
}
