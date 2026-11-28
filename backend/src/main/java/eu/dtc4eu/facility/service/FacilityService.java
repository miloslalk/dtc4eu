package eu.dtc4eu.facility.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.user.entity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import eu.dtc4eu.questionnaire.service.ProfileSchemaService;
import eu.dtc4eu.questionnaire.service.ProfileValidationService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.common.web.CsvWriter;
import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.facility.dto.FacilityDtos.AddMemberRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.CreateFacilityRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.FacilityDto;
import eu.dtc4eu.facility.dto.FacilityDtos.MemberDto;
import eu.dtc4eu.facility.dto.FacilityDtos.ParticipationDto;
import eu.dtc4eu.facility.dto.FacilityDtos.PutQcncRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.PutSagRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.QcncDto;
import eu.dtc4eu.facility.dto.FacilityDtos.SagDto;
import eu.dtc4eu.facility.dto.FacilityDtos.SetParticipationRequest;
import eu.dtc4eu.facility.dto.FacilityDtos.UpdateFacilityRequest;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.entity.FacilityCycleParticipation;
import eu.dtc4eu.facility.entity.FacilityMember;
import eu.dtc4eu.facility.entity.QcncAssessment;
import eu.dtc4eu.facility.entity.SetAnalysisGrid;
import eu.dtc4eu.facility.repository.FacilityCycleParticipationRepository;
import eu.dtc4eu.facility.repository.FacilityMemberRepository;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.facility.repository.QcncAssessmentRepository;
import eu.dtc4eu.facility.repository.SetAnalysisGridRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScope;
import eu.dtc4eu.security.DataScopeService;
import eu.dtc4eu.user.entity.User;
import eu.dtc4eu.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module 1. Every read is filtered through the caller's {@link DataScope}
 * (out-of-scope → 404, spec §6); every cycle-scoped write goes through
 * {@link CycleContext#requireWritable()}.
 */
@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final FacilityCycleParticipationRepository participationRepository;
    private final FacilityMemberRepository memberRepository;
    private final SetAnalysisGridRepository sagRepository;
    private final QcncAssessmentRepository qcncRepository;
    private final UserRepository userRepository;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;
    private final AuditService auditService;
    private final ProfileSchemaService profileSchemaService;
    private final ProfileValidationService profileValidationService;

    /* ------------------------------- facilities ------------------------------- */

    /** v0.2 register §7: a subtype must belong to its setting (RTC/STC/DAY_TC ↔ TC, …). */
    private void validateSubtype(Facility.FacilityType type, Facility.FacilitySubtype subtype) {
        if (subtype != null && subtype.setting() != type) {
            throw new eu.dtc4eu.common.error.BadRequestException(
                    "Subtype " + subtype + " does not belong to setting " + type);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<FacilityDto> list(String q, Facility.FacilityType type, Facility.FacilitySubtype subtype,
                                          String country, String city,
                                          FacilityCycleParticipation.Activity activity, Boolean active,
                                          Pageable pageable, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        Page<Facility> page = facilityRepository.findAll(
                specification(q, type, subtype, country, city, activity, active, scope), pageable);
        Map<Long, FacilityCycleParticipation> participations = participationsFor(page.getContent());
        Map<Long, String> facilitatorNames = facilitatorNames(participations);
        return PageResponse.of(page, f -> toDto(f, participations, facilitatorNames));
    }

    @Transactional(readOnly = true)
    public FacilityDto get(Long id, AppUserDetails principal) {
        Facility facility = findInScope(id, principal);
        Map<Long, FacilityCycleParticipation> participations = participationsFor(List.of(facility));
        return toDto(facility, participations, facilitatorNames(participations));
    }

    @Transactional
    public FacilityDto create(CreateFacilityRequest request, AppUserDetails actor) {
        if (facilityRepository.existsByFacilityCode(request.facilityCode())) {
            throw new ConflictException("Facility code already exists");
        }
        validateSubtype(request.facilityType(), request.facilitySubtype());
        Facility facility = new Facility();
        facility.setFacilityCode(request.facilityCode());
        facility.setFacilityType(request.facilityType());
        facility.setFacilitySubtype(request.facilitySubtype());
        facility.setName(request.name());
        facility.setProvider(request.provider());
        facility.setPlace(request.place());
        facility.setCountry(request.country());
        facility.setCity(request.city());
        facility.setAddress(request.address());
        facility = facilityRepository.save(facility);
        auditService.success(actor.getId(), "CREATE", "facility", facility.getId());
        return FacilityDto.of(facility, null, null);
    }

    @Transactional
    public FacilityDto update(Long id, UpdateFacilityRequest request, AppUserDetails actor) {
        Facility facility = findInScope(id, actor);
        validateSubtype(request.facilityType(), request.facilitySubtype());
        facility.setFacilityType(request.facilityType());
        facility.setFacilitySubtype(request.facilitySubtype());
        facility.setName(request.name());
        facility.setProvider(request.provider());
        facility.setPlace(request.place());
        facility.setCountry(request.country());
        facility.setCity(request.city());
        facility.setAddress(request.address());
        if (request.active() != null) {
            facility.setActive(request.active());
        }
        auditService.success(actor.getId(), "UPDATE", "facility", facility.getId());
        Map<Long, FacilityCycleParticipation> participations = participationsFor(List.of(facility));
        return toDto(facility, participations, facilitatorNames(participations));
    }

    @Transactional(readOnly = true)
    public String exportCsv(String q, Facility.FacilityType type, Facility.FacilitySubtype subtype,
                            String country, String city,
                            FacilityCycleParticipation.Activity activity, Boolean active,
                            AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        List<Facility> facilities = facilityRepository.findAll(
                specification(q, type, subtype, country, city, activity, active, scope),
                Sort.by("facilityCode"));
        Map<Long, FacilityCycleParticipation> participations = participationsFor(facilities);
        Map<Long, String> facilitatorNames = facilitatorNames(participations);

        CsvWriter csv = new CsvWriter()
                .row("facility_code", "setting", "subtype", "name", "provider", "place", "country", "city", "active",
                        "activity", "internal_facilitator");
        for (Facility f : facilities) {
            FacilityCycleParticipation p = participations.get(f.getId());
            csv.row(f.getFacilityCode(), f.getFacilityType(), f.getFacilitySubtype(), f.getName(), f.getProvider(),
                    f.getPlace(), f.getCountry(), f.getCity(), f.isActive(),
                    p != null ? p.getActivity() : null,
                    p != null && p.getInternalFacilitatorId() != null
                            ? facilitatorNames.get(p.getInternalFacilitatorId()) : null);
        }
        auditService.success(principal.getId(), "EXPORT", "facility", null,
                Map.of("count", facilities.size(), "cycleId", cycleContext.maybe().map(c -> c.getId()).orElse(null)));
        return csv.build();
    }

    /* ------------------------------ participation ----------------------------- */

    @Transactional(readOnly = true)
    public ParticipationDto getParticipation(Long facilityId, AppUserDetails principal) {
        findInScope(facilityId, principal);
        FacilityCycleParticipation participation = participationRepository
                .findByFacilityIdAndAnnualCycleId(facilityId, cycleContext.requireId())
                .orElseThrow(() -> new NotFoundException("No participation recorded for this cycle"));
        return participationDto(participation);
    }

    private ParticipationDto participationDto(FacilityCycleParticipation participation) {
        return new ParticipationDto(participation.getActivity(), participation.getInternalFacilitatorId(),
                userName(participation.getInternalFacilitatorId()),
                participation.getSagDone(), participation.getQcncDone(), participation.getDtcroLongFormDone(),
                participation.getFoundationMeetingDone(), participation.getFinalForumDone());
    }

    /**
     * The assigned Internal Facilitator gets data access to the facility (as
     * audit and terna coordinators do), and loses it once no cycle names them
     * there any more. Manual grants share the table and are revoked too — the
     * known limitation of the coordinator auto-scope pattern.
     */
    private void syncFacilitatorAccess(Long facilityId, Long facilitatorId, Long previousFacilitatorId) {
        if (facilitatorId != null) {
            participationRepository.grantFacilityAccess(facilitatorId, facilityId);
        }
        if (previousFacilitatorId != null && !previousFacilitatorId.equals(facilitatorId)
                && participationRepository.findByFacilityId(facilityId).stream()
                .noneMatch(p -> previousFacilitatorId.equals(p.getInternalFacilitatorId()))) {
            participationRepository.revokeFacilityAccess(previousFacilitatorId, facilityId);
        }
    }

    /** Upsert per (facility, cycle): the activity is settable per cycle (spec T2). */
    @Transactional
    public ParticipationDto setParticipation(Long facilityId, SetParticipationRequest request,
                                             AppUserDetails actor) {
        findInScope(facilityId, actor);
        Long cycleId = cycleContext.requireWritable().getId();
        Long facilitatorId = request.internalFacilitatorId();
        if (facilitatorId != null) {
            User facilitator = userRepository.findById(facilitatorId)
                    .orElseThrow(() -> new NotFoundException("Internal facilitator user not found"));
            if (!facilitator.roleCodes().contains(Role.INTERNAL_FACILITATOR)) {
                throw new BadRequestException(facilitator.getFullName() + " does not have the Internal Facilitator role");
            }
            // Simone, 24 Nov 2026: one community per Internal Facilitator per annual cycle
            participationRepository.findByAnnualCycleIdAndInternalFacilitatorId(cycleId, facilitatorId).stream()
                    .filter(other -> !other.getFacilityId().equals(facilityId))
                    .findFirst()
                    .ifPresent(other -> {
                        String code = facilityRepository.findById(other.getFacilityId())
                                .map(Facility::getFacilityCode).orElse("another community");
                        throw new BadRequestException(facilitator.getFullName()
                                + " is already the Internal Facilitator of " + code
                                + " in this cycle. A person can facilitate only one community per cycle.");
                    });
        }
        FacilityCycleParticipation participation = participationRepository
                .findByFacilityIdAndAnnualCycleId(facilityId, cycleId)
                .orElseGet(() -> {
                    FacilityCycleParticipation created = new FacilityCycleParticipation();
                    created.setFacilityId(facilityId);
                    created.setAnnualCycleId(cycleId);
                    return created;
                });
        Long previousFacilitatorId = participation.getInternalFacilitatorId();
        if (!Objects.equals(previousFacilitatorId, facilitatorId) && !isAdmin(actor)) {
            // naming a facilitator grants them the facility's data, so only admins assign one
            throw new AccessDeniedException("Only an administrator can change the Internal Facilitator");
        }
        participation.setActivity(request.activity());
        participation.setInternalFacilitatorId(request.internalFacilitatorId());
        participation.setSagDone(request.sagDone());
        participation.setQcncDone(request.qcncDone());
        participation.setDtcroLongFormDone(request.dtcroLongFormDone());
        participation.setFoundationMeetingDone(request.foundationMeetingDone());
        participation.setFinalForumDone(request.finalForumDone());
        participationRepository.saveAndFlush(participation);
        syncFacilitatorAccess(facilityId, facilitatorId, previousFacilitatorId);
        auditService.success(actor.getId(), "SET_PARTICIPATION", "facility", facilityId,
                Map.of("cycleId", cycleId, "activity", request.activity().name()));
        return participationDto(participation);
    }

    /* --------------------------------- members -------------------------------- */

    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(Long facilityId, AppUserDetails principal) {
        findInScope(facilityId, principal);
        return memberRepository
                .findByFacilityIdAndAnnualCycleIdOrderByCategoryAscMemberCodeAsc(facilityId, cycleContext.requireId())
                .stream().map(MemberDto::of).toList();
    }

    @Transactional
    public MemberDto addMember(Long facilityId, AddMemberRequest request, AppUserDetails actor) {
        findInScope(facilityId, actor);
        Long cycleId = cycleContext.requireWritable().getId();
        checkSingleCommunity(facilityId, cycleId, request.memberCode(), request.category());
        FacilityMember member = new FacilityMember();
        member.setFacilityId(facilityId);
        member.setAnnualCycleId(cycleId);
        member.setMemberCode(request.memberCode());
        member.setCategory(request.category());
        member = memberRepository.save(member);
        auditService.success(actor.getId(), "ADD_MEMBER", "facility", facilityId,
                Map.of("memberCode", member.getMemberCode(), "category", member.getCategory().name()));
        return MemberDto.of(member);
    }

    /**
     * Simone, 24 Nov 2026: Staff and Users are members of a single community per
     * cycle; Family members and Managers may also belong to other communities.
     * So a code is registered once per facility, a STAFF/USER code nowhere else,
     * and no other facility may register a code that is STAFF/USER somewhere.
     */
    private void checkSingleCommunity(Long facilityId, Long cycleId, String code, FacilityMember.Category category) {
        boolean singleCommunity = category == FacilityMember.Category.STAFF || category == FacilityMember.Category.USER;
        for (FacilityMember existing : memberRepository.findByAnnualCycleIdAndMemberCodeIgnoreCase(cycleId, code)) {
            if (existing.getFacilityId().equals(facilityId)) {
                throw new ConflictException("Member code " + code + " is already registered in this community");
            }
            boolean existingSingle = existing.getCategory() == FacilityMember.Category.STAFF
                    || existing.getCategory() == FacilityMember.Category.USER;
            if (singleCommunity || existingSingle) {
                String other = facilityRepository.findById(existing.getFacilityId())
                        .map(Facility::getFacilityCode).orElse("another community");
                throw new ConflictException("Member code " + code + " is already registered in " + other
                        + " as " + existing.getCategory() + " in this cycle. Staff and Users belong to one"
                        + " community per cycle; only Family members and Managers can be in several.");
            }
        }
    }

    @Transactional
    public void removeMember(Long facilityId, Long memberId, AppUserDetails actor) {
        findInScope(facilityId, actor);
        cycleContext.requireWritable();
        FacilityMember member = memberRepository.findById(memberId)
                .filter(m -> m.getFacilityId().equals(facilityId))
                .orElseThrow(() -> NotFoundException.of("Member", memberId));
        memberRepository.delete(member);
        auditService.success(actor.getId(), "REMOVE_MEMBER", "facility", facilityId,
                Map.of("memberCode", member.getMemberCode()));
    }

    /* ---------------------------- set analysis grid ---------------------------- */

    /** The structured GAS-Set rides set_analysis_grids as grid_variant 'GAS'. */
    public static final String GAS_VARIANT = "GAS";
    private static final String GAS_SCHEMA_CODE = "gasset";
    private static final String GAS_SCHEMA_FORM = "GAS_SET";

    /**
     * Clinical access to GAS-Set Section N (per-user clinical data, register
     * §11.7): admins, the research team, and an internal facilitator for the
     * facilities a cycle names them facilitator of (not every facility their
     * other roles let them see).
     */
    private boolean hasClinicalAccess(Long facilityId, AppUserDetails principal) {
        if (isAdmin(principal) || principal.hasRole(Role.RESEARCH_TEAM)) {
            return true;
        }
        return principal.hasRole(Role.INTERNAL_FACILITATOR) && participationRepository.findByFacilityId(facilityId)
                .stream().anyMatch(p -> principal.getId().equals(p.getInternalFacilitatorId()));
    }

    private static boolean isAdmin(AppUserDetails principal) {
        return principal.hasRole(Role.ADMIN) || principal.hasRole(Role.SUPER_ADMIN);
    }

    @Transactional(readOnly = true)
    public JsonNode getSagSchema(Long facilityId, AppUserDetails principal) {
        findInScope(facilityId, principal);
        JsonNode schema = profileSchemaService.find(GAS_SCHEMA_CODE, GAS_SCHEMA_FORM)
                .orElseThrow(() -> new NotFoundException("GAS-Set schema not available"));
        if (hasClinicalAccess(facilityId, principal)) {
            return schema;
        }
        JsonNode copy = schema.deepCopy();
        com.fasterxml.jackson.databind.node.ArrayNode sections =
                (com.fasterxml.jackson.databind.node.ArrayNode) copy.path("sections");
        for (int i = sections.size() - 1; i >= 0; i--) {
            if (sections.get(i).path("restricted").asBoolean(false)) {
                sections.remove(i);
            }
        }
        return copy;
    }

    /** Top-level field keys of restricted sections — stripped from GAS payloads for non-clinical readers. */
    private java.util.Set<String> restrictedGasKeys() {
        return profileSchemaService.find(GAS_SCHEMA_CODE, GAS_SCHEMA_FORM)
                .map(schema -> {
                    java.util.Set<String> keys = new java.util.HashSet<String>();
                    for (JsonNode section : schema.path("sections")) {
                        if (section.path("restricted").asBoolean(false)) {
                            section.path("fields").forEach(f -> keys.add(f.path("key").asText()));
                        }
                    }
                    return keys;
                })
                .orElse(java.util.Set.of());
    }

    private SagDto sagDto(SetAnalysisGrid sag, boolean clinical) {
        SagDto dto = SagDto.of(sag);
        if (clinical || !GAS_VARIANT.equals(sag.getGridVariant()) || dto.payload() == null) {
            return dto;
        }
        Map<String, Object> filtered = new java.util.LinkedHashMap<>(dto.payload());
        filtered.keySet().removeAll(restrictedGasKeys());
        return new SagDto(dto.id(), dto.gridVariant(), dto.compiledBy(), filtered);
    }

    @Transactional(readOnly = true)
    public List<SagDto> getSags(Long facilityId, AppUserDetails principal) {
        findInScope(facilityId, principal);
        boolean clinical = hasClinicalAccess(facilityId, principal);
        return sagRepository
                .findByFacilityIdAndAnnualCycleIdOrderByGridVariantAsc(facilityId, cycleContext.requireId())
                .stream().map(sag -> sagDto(sag, clinical)).toList();
    }

    @Transactional
    public SagDto putSag(Long facilityId, PutSagRequest request, AppUserDetails actor) {
        findInScope(facilityId, actor);
        Long cycleId = cycleContext.requireWritable().getId();
        SetAnalysisGrid grid = sagRepository
                .findByFacilityIdAndAnnualCycleIdAndGridVariant(facilityId, cycleId, request.gridVariant())
                .orElseGet(() -> {
                    SetAnalysisGrid created = new SetAnalysisGrid();
                    created.setFacilityId(facilityId);
                    created.setAnnualCycleId(cycleId);
                    created.setGridVariant(request.gridVariant());
                    return created;
                });
        Map<String, Object> payload = request.payload();
        if (GAS_VARIANT.equals(request.gridVariant())) {
            // lenient validation: the GAS-Set is compiled progressively across the cycle
            Optional<JsonNode> schema = profileSchemaService.find(GAS_SCHEMA_CODE, GAS_SCHEMA_FORM);
            if (schema.isPresent()) {
                // L/M rows: the disciplinary area follows from the role (Tables 1-2)
                payload = ProfileDerivations.apply(payload, schema.get());
                profileValidationService.validate(payload, schema.get(), false);
            }
        }
        grid.setPayload(payload);
        grid.setCompiledBy(actor.getId());
        grid = sagRepository.save(grid);
        auditService.success(actor.getId(), "PUT_SAG", "facility", facilityId,
                Map.of("cycleId", cycleId, "variant", request.gridVariant()));
        return SagDto.of(grid);
    }

    /* ----------------------------------- qcnc ---------------------------------- */

    @Transactional(readOnly = true)
    public QcncDto getQcnc(Long facilityId, AppUserDetails principal) {
        findInScope(facilityId, principal);
        QcncAssessment qcnc = qcncRepository
                .findByFacilityIdAndAnnualCycleIdAndAdministrationType(facilityId, cycleContext.requireId(),
                        QcncAssessment.AdministrationType.INTERNAL_FACILITATOR)
                .orElseThrow(() -> new NotFoundException("No QCNC assessment recorded for this cycle"));
        return QcncDto.of(qcnc);
    }

    @Transactional
    public QcncDto putQcnc(Long facilityId, PutQcncRequest request, AppUserDetails actor) {
        findInScope(facilityId, actor);
        Long cycleId = cycleContext.requireWritable().getId();
        QcncAssessment qcnc = qcncRepository
                .findByFacilityIdAndAnnualCycleIdAndAdministrationType(facilityId, cycleId,
                        QcncAssessment.AdministrationType.INTERNAL_FACILITATOR)
                .orElseGet(() -> {
                    QcncAssessment created = new QcncAssessment();
                    created.setFacilityId(facilityId);
                    created.setAnnualCycleId(cycleId);
                    return created;
                });
        qcnc.setPayload(request.payload());
        qcnc = qcncRepository.save(qcnc);
        auditService.success(actor.getId(), "PUT_QCNC", "facility", facilityId, Map.of("cycleId", cycleId));
        return QcncDto.of(qcnc);
    }

    /* --------------------------------- helpers -------------------------------- */

    Facility findInScope(Long id, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        return facilityRepository.findById(id)
                .filter(f -> scope.canSeeFacility(f.getId()))
                .orElseThrow(() -> NotFoundException.of("Facility", id));
    }

    private Specification<Facility> specification(String q, Facility.FacilityType type,
                                                  Facility.FacilitySubtype subtype, String country, String city,
                                                  FacilityCycleParticipation.Activity activity, Boolean active,
                                                  DataScope scope) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!scope.all()) {
                if (scope.facilityIds().isEmpty()) {
                    predicates.add(cb.disjunction()); // sees nothing
                } else {
                    predicates.add(root.get("id").in(scope.facilityIds()));
                }
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("facilityCode")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("facilityType"), type));
            }
            if (subtype != null) {
                predicates.add(cb.equal(root.get("facilitySubtype"), subtype));
            }
            if (country != null && !country.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("country")), "%" + country.trim().toLowerCase() + "%"));
            }
            if (city != null && !city.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("city")), "%" + city.trim().toLowerCase() + "%"));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (activity != null) {
                // participation in the selected cycle with the given activity (spec T2 filters)
                Long cycleId = cycleContext.requireId();
                Subquery<Long> sub = query.subquery(Long.class);
                Root<FacilityCycleParticipation> p = sub.from(FacilityCycleParticipation.class);
                sub.select(p.get("facilityId"))
                        .where(cb.equal(p.get("facilityId"), root.get("id")),
                                cb.equal(p.get("annualCycleId"), cycleId),
                                cb.equal(p.get("activity"), activity));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<Long, FacilityCycleParticipation> participationsFor(List<Facility> facilities) {
        if (facilities.isEmpty() || cycleContext.maybe().isEmpty()) {
            return Map.of();
        }
        List<Long> ids = facilities.stream().map(Facility::getId).toList();
        return participationRepository.findByAnnualCycleIdAndFacilityIdIn(cycleContext.requireId(), ids).stream()
                .collect(Collectors.toMap(FacilityCycleParticipation::getFacilityId, Function.identity()));
    }

    private Map<Long, String> facilitatorNames(Map<Long, FacilityCycleParticipation> participations) {
        List<Long> userIds = participations.values().stream()
                .map(FacilityCycleParticipation::getInternalFacilitatorId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private FacilityDto toDto(Facility facility, Map<Long, FacilityCycleParticipation> participations,
                              Map<Long, String> facilitatorNames) {
        FacilityCycleParticipation participation = participations.get(facility.getId());
        String facilitatorName = participation != null && participation.getInternalFacilitatorId() != null
                ? facilitatorNames.get(participation.getInternalFacilitatorId()) : null;
        return FacilityDto.of(facility, participation, facilitatorName);
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getFullName).orElse(null);
    }
}
