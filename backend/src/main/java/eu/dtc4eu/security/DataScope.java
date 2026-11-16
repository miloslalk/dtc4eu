package eu.dtc4eu.security;

import java.util.Set;

/**
 * Resolved data visibility of the calling user (spec §6). {@code all} short-
 * circuits the id sets; respondent-only users see just records they created.
 * {@code respondent} marks RESPONDENT users; {@code respondentFacilityIds} are
 * the facilities they are bound to: they may answer questionnaires for them but
 * never read their data.
 */
public record DataScope(Long userId, boolean all, Set<Long> facilityIds, Set<Long> ternaIds,
                        boolean respondent, Set<Long> respondentFacilityIds, boolean respondentOnly) {

    public boolean canSeeFacility(Long facilityId) {
        return all || facilityIds.contains(facilityId);
    }

    public boolean canSeeTerna(Long ternaId) {
        return all || ternaIds.contains(ternaId);
    }

    /** Responses may be opened for facilities in scope or those a respondent is bound to. */
    public boolean canRespondFor(Long facilityId) {
        return canSeeFacility(facilityId) || respondentFacilityIds.contains(facilityId);
    }

    /** Facility-less responses: any data scope at all, or a respondent's own self-report. */
    public boolean canOpenResponses() {
        return all || !facilityIds.isEmpty() || !ternaIds.isEmpty() || respondent;
    }
}
