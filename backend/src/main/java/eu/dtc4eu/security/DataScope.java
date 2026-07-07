package eu.dtc4eu.security;

import java.util.Set;

/**
 * Resolved data visibility of the calling user (spec §6). {@code all} short-
 * circuits the id sets; respondent-only users see just records they created.
 */
public record DataScope(Long userId, boolean all, Set<Long> facilityIds, Set<Long> ternaIds, boolean respondentOnly) {

    public boolean canSeeFacility(Long facilityId) {
        return all || facilityIds.contains(facilityId);
    }

    public boolean canSeeTerna(Long ternaId) {
        return all || ternaIds.contains(ternaId);
    }
}
