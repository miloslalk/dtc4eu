package eu.dtc4eu.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * The cycles holding data the caller can see — ternas, facility participation
 * or questionnaire responses within their scope. The frontend uses it to open
 * a first-time visitor on a cycle with their data instead of an empty newer one
 * (Simone's feedback #1). Plain SQL keeps the security slice free of new
 * dependencies on the domain packages.
 */
@Service
@RequiredArgsConstructor
public class CyclesWithDataService {

    private static final List<Long> NONE = List.of(-1L);

    private final NamedParameterJdbcTemplate jdbc;
    private final DataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public List<Long> cycleIds(AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        if (scope.all()) {
            return jdbc.queryForList("select c.id from annual_cycles c where"
                    + " exists (select 1 from ternas t where t.annual_cycle_id = c.id)"
                    + " or exists (select 1 from facility_cycle_participation p where p.annual_cycle_id = c.id)"
                    + " or exists (select 1 from questionnaire_responses r where r.annual_cycle_id = c.id)"
                    + " order by c.start_date desc", new MapSqlParameterSource(), Long.class);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("facilityIds", orNone(scope.facilityIds()))
                .addValue("ternaIds", orNone(scope.ternaIds()))
                .addValue("userId", principal.getId());
        return jdbc.queryForList("select c.id from annual_cycles c where"
                + " exists (select 1 from ternas t where t.annual_cycle_id = c.id and t.id in (:ternaIds))"
                + " or exists (select 1 from facility_cycle_participation p where p.annual_cycle_id = c.id"
                + "   and (p.facility_id in (:facilityIds) or p.internal_facilitator_id = :userId))"
                + " or exists (select 1 from questionnaire_responses r where r.annual_cycle_id = c.id"
                + "   and (r.facility_id in (:facilityIds) or r.created_by = :userId))"
                + " order by c.start_date desc", params, Long.class);
    }

    private static List<Long> orNone(Set<Long> ids) {
        return ids.isEmpty() ? NONE : List.copyOf(ids);
    }
}
