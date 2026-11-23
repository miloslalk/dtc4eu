package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.QuestionnaireResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionnaireResponseRepository
        extends JpaRepository<QuestionnaireResponse, Long>, JpaSpecificationExecutor<QuestionnaireResponse> {

    List<QuestionnaireResponse> findByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndStatus(
            Long facilityId, Long annualCycleId, Long questionnaireId, QuestionnaireResponse.Status status);

    long countByFacilityIdAndAnnualCycleIdAndQuestionnaireIdAndRespondentCategoryAndStatus(
            Long facilityId, Long annualCycleId, Long questionnaireId,
            QuestionnaireResponse.RespondentCategory respondentCategory, QuestionnaireResponse.Status status);

    boolean existsByQuestionnaireId(Long questionnaireId);

    /** Existing responses of the given subject codes on one form in one cycle (import duplicate check). */
    List<QuestionnaireResponse> findByAnnualCycleIdAndFormIdAndSubjectCodeIn(Long annualCycleId, Long formId,
                                                                            Collection<String> subjectCodes);

    /**
     * The facility's pseudonymous member roster for a cycle (import "unknown subject code" check).
     * Native SQL keeps the questionnaire domain free of a Java dependency on the facility entities.
     */
    @Query(value = "select m.member_code from facility_members m"
            + " where m.facility_id = :facilityId and m.annual_cycle_id = :cycleId", nativeQuery = true)
    List<String> findRosterCodes(@Param("facilityId") Long facilityId,
                                 @Param("cycleId") Long cycleId);

    @Query(value = "select f.facility_code from facilities f where f.id = :id",
            nativeQuery = true)
    Optional<String> findFacilityCode(@Param("id") Long id);

    /** All facility id → code pairs, for exports (native: no Java dependency on the facility entities). */
    @Query(value = "select f.id, f.facility_code from facilities f", nativeQuery = true)
    List<Object[]> findAllFacilityCodes();

    /** A respondent's own responses in a cycle — the status of their assigned tasks (spec COL-03). */
    List<QuestionnaireResponse> findByCreatedByAndAnnualCycleId(Long createdBy, Long annualCycleId);

    long countByFacilityIdAndAnnualCycleIdAndRespondentCategoryAndStatus(
            Long facilityId, Long annualCycleId,
            QuestionnaireResponse.RespondentCategory respondentCategory, QuestionnaireResponse.Status status);
}
