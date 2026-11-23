package eu.dtc4eu.progress.service;

import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.progress.dto.ProgressDtos.FacilityProgressDto;
import eu.dtc4eu.progress.dto.ProgressDtos.Task;
import eu.dtc4eu.progress.dto.ProgressDtos.TaskDto;
import eu.dtc4eu.progress.dto.ProgressDtos.TaskStatus;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScope;
import eu.dtc4eu.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Completion status of each facility's instruments in the selected cycle
 * (Simone's feedback #5). It feeds the role-based "what you need to
 * complete" dashboard and the terna × instrument matrix. Read-only SQL over
 * the registry, QCNC, GAS-Set and response tables keeps this slice free of
 * Java dependencies on those domains (frozen ArchUnit cycles).
 *
 * <p>Status rules: a checklist OK or a submitted form is DONE; any saved data
 * (a grid, an administration, a draft) is IN_PROGRESS; nothing is NOT_STARTED.
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

    private static final List<Long> NONE = List.of(-1L);

    private final NamedParameterJdbcTemplate jdbc;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;

    /** The caller's facilities: everything in the cycle's ternas for global roles, otherwise their scope. */
    @Transactional(readOnly = true)
    public List<FacilityProgressDto> mine(AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        Long cycleId = cycleContext.requireId();
        MapSqlParameterSource params = new MapSqlParameterSource("cycleId", cycleId)
                .addValue("userId", principal.getId())
                .addValue("facilityIds", scope.facilityIds().isEmpty() ? NONE : List.copyOf(scope.facilityIds()))
                .addValue("ternaIds", scope.ternaIds().isEmpty() ? NONE : List.copyOf(scope.ternaIds()));
        String where = scope.all()
                ? "(tf.id is not null or p.id is not null)"
                : "(f.id in (:facilityIds) or tf.terna_id in (:ternaIds) or p.internal_facilitator_id = :userId)";
        return rows(where, params);
    }

    /** One terna's three facilities, for the terna matrix. */
    @Transactional(readOnly = true)
    public List<FacilityProgressDto> terna(Long ternaId, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        Long cycleId = cycleContext.requireId();
        Integer inCycle = jdbc.queryForObject("select count(*) from ternas where id = :id and annual_cycle_id = :cycleId",
                new MapSqlParameterSource("id", ternaId).addValue("cycleId", cycleId), Integer.class);
        if (inCycle == null || inCycle == 0 || !scope.canSeeTerna(ternaId)) {
            throw NotFoundException.of("Terna", ternaId);
        }
        return rows("tf.terna_id = :ternaId", new MapSqlParameterSource("cycleId", cycleId).addValue("ternaId", ternaId));
    }

    private List<FacilityProgressDto> rows(String where, MapSqlParameterSource params) {
        String sql = """
                select f.id as facility_id, f.facility_code, f.name as facility_name,
                       t.id as terna_id, t.terna_code, u.full_name as facilitator,
                       p.sag_done, p.qcnc_done, p.dtcro_long_form_done,
                       (coalesce(p.sag_done, false)::int + coalesce(p.qcnc_done, false)::int
                        + coalesce(p.dtcro_long_form_done, false)::int + coalesce(p.foundation_meeting_done, false)::int
                        + coalesce(p.final_forum_done, false)::int) as checklist_ok,
                       (p.id is not null) as has_participation,
                       exists (select 1 from set_analysis_grids g where g.facility_id = f.id
                               and g.annual_cycle_id = :cycleId and g.grid_variant = 'GAS') as gas_saved,
                       (select count(*) from qcnc_assessments q where q.facility_id = f.id and q.annual_cycle_id = :cycleId
                               and q.administration_type = 'INTERNAL_FACILITATOR') as qcnc_if,
                       (select count(*) from qcnc_assessments q where q.facility_id = f.id and q.annual_cycle_id = :cycleId
                               and q.administration_type = 'TERNA_COORDINATOR_OWN') as qcnc_own,
                       (select count(*) from qcnc_assessments q where q.facility_id = f.id and q.annual_cycle_id = :cycleId
                               and q.administration_type = 'TERNA_COORDINATOR_DELEGATION') as qcnc_delegation,
                       (select count(*) from questionnaire_responses r where r.facility_id = f.id
                               and r.annual_cycle_id = :cycleId and r.respondent_category = 'LONG_FORM'
                               and r.status = 'SUBMITTED') as long_form_submitted,
                       (select count(*) from questionnaire_responses r where r.facility_id = f.id
                               and r.annual_cycle_id = :cycleId and r.respondent_category = 'LONG_FORM') as long_form_any,
                       (select count(*) from questionnaire_responses r where r.facility_id = f.id
                               and r.annual_cycle_id = :cycleId and r.respondent_category = 'DELEGATION'
                               and r.status = 'SUBMITTED') as delegation_submitted,
                       (select count(*) from questionnaire_responses r where r.facility_id = f.id
                               and r.annual_cycle_id = :cycleId and r.respondent_category = 'DELEGATION') as delegation_any,
                       (select count(*) from standard_applications s where s.facility_id = f.id
                               and s.annual_cycle_id = :cycleId) as documents,
                       (select count(*) from standard_applications s where s.facility_id = f.id
                               and s.annual_cycle_id = :cycleId and s.assessed_by is not null) as documents_assessed
                  from facilities f
                  left join terna_facilities tf on tf.facility_id = f.id
                       and tf.terna_id in (select id from ternas where annual_cycle_id = :cycleId)
                  left join ternas t on t.id = tf.terna_id
                  left join facility_cycle_participation p on p.facility_id = f.id and p.annual_cycle_id = :cycleId
                  left join users u on u.id = p.internal_facilitator_id
                 where %s
                 order by t.terna_code nulls last, tf.position nulls last, f.facility_code
                """.formatted(where);
        return jdbc.queryForList(sql, params).stream().map(ProgressService::toDto).toList();
    }

    private static FacilityProgressDto toDto(Map<String, Object> row) {
        List<TaskDto> tasks = new ArrayList<>();
        tasks.add(task(Task.GAS_SET, flag(row, "sag_done"), flag(row, "gas_saved") ? 1 : 0));
        tasks.add(task(Task.QCNC_INTERNAL_FACILITATOR, flag(row, "qcnc_done"), count(row, "qcnc_if")));
        tasks.add(submitted(Task.DTCRO_LONG_FORM, count(row, "long_form_submitted"), count(row, "long_form_any"),
                flag(row, "dtcro_long_form_done")));
        tasks.add(task(Task.QCNC_TERNA_COORDINATOR_OWN, count(row, "qcnc_own") > 0, count(row, "qcnc_own")));
        tasks.add(task(Task.QCNC_TERNA_COORDINATOR_DELEGATION, count(row, "qcnc_delegation") > 0,
                count(row, "qcnc_delegation")));
        tasks.add(submitted(Task.DTCRO_DELEGATION, count(row, "delegation_submitted"), count(row, "delegation_any"),
                false));
        int documents = count(row, "documents");
        int assessed = count(row, "documents_assessed");
        tasks.add(new TaskDto(Task.QUALITY_DOCUMENTS, documents == 0 ? TaskStatus.NOT_STARTED
                : assessed == documents ? TaskStatus.DONE : TaskStatus.IN_PROGRESS, assessed));
        int checklist = count(row, "checklist_ok");
        tasks.add(new TaskDto(Task.PARTICIPATION_CHECKLIST, checklist == 5 ? TaskStatus.DONE
                : checklist > 0 || flag(row, "has_participation") ? TaskStatus.IN_PROGRESS : TaskStatus.NOT_STARTED,
                checklist));
        return new FacilityProgressDto(((Number) row.get("facility_id")).longValue(), (String) row.get("facility_code"),
                (String) row.get("facility_name"),
                row.get("terna_id") == null ? null : ((Number) row.get("terna_id")).longValue(),
                (String) row.get("terna_code"), (String) row.get("facilitator"), tasks);
    }

    /** Saved data without the checklist OK is in progress; the checklist OK alone also counts as done. */
    private static TaskDto task(Task task, boolean done, int saved) {
        return new TaskDto(task, done ? TaskStatus.DONE : saved > 0 ? TaskStatus.IN_PROGRESS : TaskStatus.NOT_STARTED,
                saved);
    }

    private static TaskDto submitted(Task task, int submitted, int any, boolean checklistDone) {
        TaskStatus status = submitted > 0 || checklistDone ? TaskStatus.DONE
                : any > 0 ? TaskStatus.IN_PROGRESS : TaskStatus.NOT_STARTED;
        return new TaskDto(task, status, submitted);
    }

    private static boolean flag(Map<String, Object> row, String column) {
        return Boolean.TRUE.equals(row.get(column));
    }

    private static int count(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0 : ((Number) value).intValue();
    }
}
