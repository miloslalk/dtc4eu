package eu.dtc4eu.facility.dto;

import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.entity.FacilityCycleParticipation;
import eu.dtc4eu.facility.entity.FacilityMember;
import eu.dtc4eu.facility.entity.QcncAssessment;
import eu.dtc4eu.facility.entity.SetAnalysisGrid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public final class FacilityDtos {

    private FacilityDtos() {
    }

    /** List/detail row; activity + facilitator reflect the selected cycle. */
    public record FacilityDto(Long id, String facilityCode, Facility.FacilityType facilityType,
                              Facility.FacilitySubtype facilitySubtype, String name,
                              String provider, String place, String country, String city, String address,
                              boolean active, FacilityCycleParticipation.Activity activity,
                              Long internalFacilitatorId, String internalFacilitatorName) {

        public static FacilityDto of(Facility f, FacilityCycleParticipation participation, String facilitatorName) {
            return new FacilityDto(f.getId(), f.getFacilityCode(), f.getFacilityType(), f.getFacilitySubtype(),
                    f.getName(), f.getProvider(),
                    f.getPlace(), f.getCountry(), f.getCity(), f.getAddress(), f.isActive(),
                    participation != null ? participation.getActivity() : null,
                    participation != null ? participation.getInternalFacilitatorId() : null,
                    facilitatorName);
        }
    }

    public record CreateFacilityRequest(
            @NotBlank @Size(max = 40) String facilityCode,
            @NotNull Facility.FacilityType facilityType,
            Facility.FacilitySubtype facilitySubtype,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 255) String provider,
            @Size(max = 200) String place,
            @Size(max = 80) String country,
            @Size(max = 120) String city,
            String address) {
    }

    public record UpdateFacilityRequest(
            @NotNull Facility.FacilityType facilityType,
            Facility.FacilitySubtype facilitySubtype,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 255) String provider,
            @Size(max = 200) String place,
            @Size(max = 80) String country,
            @Size(max = 120) String city,
            String address,
            Boolean active) {
    }

    public record ParticipationDto(FacilityCycleParticipation.Activity activity, Long internalFacilitatorId,
                                   String internalFacilitatorName,
                                   Boolean sagDone, Boolean qcncDone, Boolean dtcroLongFormDone,
                                   Boolean foundationMeetingDone, Boolean finalForumDone) {
    }

    public record SetParticipationRequest(@NotNull FacilityCycleParticipation.Activity activity,
                                          Long internalFacilitatorId,
                                          Boolean sagDone, Boolean qcncDone, Boolean dtcroLongFormDone,
                                          Boolean foundationMeetingDone, Boolean finalForumDone) {
    }

    public record MemberDto(Long id, String memberCode, FacilityMember.Category category) {

        public static MemberDto of(FacilityMember member) {
            return new MemberDto(member.getId(), member.getMemberCode(), member.getCategory());
        }
    }

    public record AddMemberRequest(@NotBlank @Size(max = 40) String memberCode,
                                   @NotNull FacilityMember.Category category) {
    }

    public record SagDto(Long id, String gridVariant, Long compiledBy, Map<String, Object> payload) {

        public static SagDto of(SetAnalysisGrid grid) {
            return new SagDto(grid.getId(), grid.getGridVariant(), grid.getCompiledBy(), grid.getPayload());
        }
    }

    public record PutSagRequest(@NotBlank @Pattern(regexp = "[A-D]", message = "must be A, B, C or D") String gridVariant,
                                Map<String, Object> payload) {
    }

    public record QcncDto(Long id, Map<String, Object> payload) {

        public static QcncDto of(QcncAssessment qcnc) {
            return new QcncDto(qcnc.getId(), qcnc.getPayload());
        }
    }

    public record PutQcncRequest(Map<String, Object> payload) {
    }
}
