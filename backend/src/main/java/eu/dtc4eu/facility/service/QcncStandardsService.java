package eu.dtc4eu.facility.service;

import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.facility.dto.QcncDtos.AdministrationDto;
import eu.dtc4eu.facility.dto.QcncDtos.MetaAreaDto;
import eu.dtc4eu.facility.dto.QcncDtos.MetaAreaStatus;
import eu.dtc4eu.facility.dto.QcncDtos.QcncBoardDto;
import eu.dtc4eu.facility.dto.QcncDtos.SaveResultsRequest;
import eu.dtc4eu.facility.dto.QcncDtos.StandardDto;
import eu.dtc4eu.facility.entity.QcncAssessment;
import eu.dtc4eu.facility.entity.QcncStandard;
import eu.dtc4eu.facility.entity.QcncStandardResult;
import eu.dtc4eu.facility.repository.QcncAssessmentRepository;
import eu.dtc4eu.facility.repository.QcncStandardRepository;
import eu.dtc4eu.facility.repository.QcncStandardResultRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.user.repository.UserRepository;
import eu.dtc4eu.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The C/NC board of the three QCNC administrations per facility+cycle
 * (internal facilitator self-assessment, terna coordinator's own, terna
 * coordinator for the visiting delegation), over the seeded V11 catalogue.
 * Items are answered with C or NC only (Simone, 24 Nov 2026: "there are no
 * scores to assign"), so the V13 1–4 scores are neither accepted nor reported.
 *
 * Meta lists per the expert feedback ("Meta-area of Strengths/weakness in
 * all three QCNC questionnaires administered") are computed at STANDARD
 * level (C / NC in all three administrations) and at META-AREA level
 * (spec §6.5, see {@link #metaAreas}); the meta-areas feed the Summary
 * Table's qualitative strength/weakness areas.
 */
@Service
@RequiredArgsConstructor
public class QcncStandardsService {

    /** Spec §6.5: meta-area → QCNC clusters and the DTCRO areas it maps to. */
    private static final List<MetaAreaDefinition> META_AREAS = List.of(
            new MetaAreaDefinition("RO.1", List.of("Q1", "Q2"), List.of(1, 2, 3)),
            new MetaAreaDefinition("RO.2", List.of("Q3"), List.of(4, 5, 6)),
            new MetaAreaDefinition("RO.3", List.of("Q4", "Q5"), List.of(7, 8, 9)));

    private record MetaAreaDefinition(String code, List<String> clusterCodes, List<Integer> dtcroAreas) {
    }

    private final QcncStandardRepository standardRepository;
    private final QcncAssessmentRepository assessmentRepository;
    private final QcncStandardResultRepository resultRepository;
    private final UserRepository userRepository;
    private final FacilityService facilityService;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<StandardDto> catalogue() {
        return standardRepository.findAllByOrderBySortOrderAsc().stream().map(StandardDto::of).toList();
    }

    @Transactional(readOnly = true)
    public QcncBoardDto getBoard(Long facilityId, AppUserDetails principal) {
        facilityService.findInScope(facilityId, principal);
        Long cycleId = cycleContext.requireId();

        List<StandardDto> standards = standardRepository.findAllByOrderBySortOrderAsc().stream()
                .map(StandardDto::of)
                .toList();

        List<QcncAssessment> assessments =
                assessmentRepository.findByFacilityIdAndAnnualCycleId(facilityId, cycleId);
        Map<Long, List<QcncStandardResult>> resultsByAssessment = assessments.isEmpty() ? Map.of()
                : resultRepository.findByAssessmentIdIn(assessments.stream().map(QcncAssessment::getId).toList())
                        .stream().collect(Collectors.groupingBy(QcncStandardResult::getAssessmentId));

        Map<QcncAssessment.AdministrationType, AdministrationDto> administrations =
                new EnumMap<>(QcncAssessment.AdministrationType.class);
        for (QcncAssessment assessment : assessments) {
            Map<Long, String> conformityByStandard = new LinkedHashMap<>();
            for (QcncStandardResult result : resultsByAssessment.getOrDefault(assessment.getId(), List.of())) {
                conformityByStandard.putIfAbsent(result.getStandardId(), result.getConformity().name());
            }
            administrations.put(assessment.getAdministrationType(), new AdministrationDto(
                    assessment.getAdministrationType(),
                    assessment.getAdministeredBy(),
                    userName(assessment.getAdministeredBy()),
                    assessment.getUpdatedAt(),
                    conformityByStandard));
        }

        return new QcncBoardDto(standards, administrations.values().stream().toList(),
                metaList(administrations, "C"), metaList(administrations, "NC"),
                metaAreas(standards, administrations));
    }

    /**
     * Qualitative meta-areas (spec §6.5). Only the standards required for
     * scoring count: in a cluster that flags mandatory standards (Q1's ★) those
     * are the required ones, otherwise every standard of the cluster. Per
     * administration, a meta-area is strong when all its required standards
     * are C and weak when fewer than half are; the facility status needs the
     * same verdict in all three administrations, else it is INCOMPLETE (an
     * administration is missing) or NEITHER.
     */
    static List<MetaAreaDto> metaAreas(List<StandardDto> standards,
                                       Map<QcncAssessment.AdministrationType, AdministrationDto> administrations) {
        Set<String> clustersWithMandatory = standards.stream()
                .filter(StandardDto::mandatory)
                .map(StandardDto::clusterCode)
                .collect(Collectors.toSet());
        return META_AREAS.stream().map(definition -> {
            List<Long> required = standards.stream()
                    .filter(s -> definition.clusterCodes().contains(s.clusterCode()))
                    .filter(s -> s.mandatory() || !clustersWithMandatory.contains(s.clusterCode()))
                    .map(StandardDto::id)
                    .toList();
            Map<QcncAssessment.AdministrationType, Integer> conforming =
                    new EnumMap<>(QcncAssessment.AdministrationType.class);
            administrations.forEach((type, administration) -> conforming.put(type, (int) required.stream()
                    .filter(id -> "C".equals(administration.conformityByStandard().get(id)))
                    .count()));
            return new MetaAreaDto(definition.code(), definition.clusterCodes(), definition.dtcroAreas(),
                    required.size(), conforming, metaAreaStatus(required.size(), conforming));
        }).toList();
    }

    private static MetaAreaStatus metaAreaStatus(int required,
                                                 Map<QcncAssessment.AdministrationType, Integer> conforming) {
        if (required == 0 || conforming.size() < QcncAssessment.AdministrationType.values().length) {
            return MetaAreaStatus.INCOMPLETE;
        }
        if (conforming.values().stream().allMatch(count -> count == required)) {
            return MetaAreaStatus.STRENGTH;
        }
        if (conforming.values().stream().allMatch(count -> count * 2 < required)) {
            return MetaAreaStatus.WEAKNESS;
        }
        return MetaAreaStatus.NEITHER;
    }

    /** Standards with the given mark in ALL THREE administrations (empty until all three exist). */
    private List<Long> metaList(Map<QcncAssessment.AdministrationType, AdministrationDto> administrations,
                                String mark) {
        if (administrations.size() < QcncAssessment.AdministrationType.values().length) {
            return List.of();
        }
        Set<Long> intersection = null;
        for (AdministrationDto administration : administrations.values()) {
            Set<Long> marked = administration.conformityByStandard().entrySet().stream()
                    .filter(e -> mark.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            intersection = intersection == null ? marked : new HashSet<>(intersection) {{
                retainAll(marked);
            }};
        }
        return intersection == null ? List.of() : intersection.stream().sorted().toList();
    }

    @Transactional
    public QcncBoardDto saveResults(Long facilityId, QcncAssessment.AdministrationType administrationType,
                                    SaveResultsRequest request, AppUserDetails actor) {
        facilityService.findInScope(facilityId, actor);
        Long cycleId = cycleContext.requireWritable().getId();

        Set<Long> known = standardRepository.findAll().stream().map(QcncStandard::getId)
                .collect(Collectors.toSet());
        for (SaveResultsRequest.ResultEntry entry : request.results()) {
            if (!known.contains(entry.standardId())) {
                throw new BadRequestException("Unknown standard id " + entry.standardId());
            }
            if (entry.score() != null) {
                throw new BadRequestException("QCNC standards are answered with C or NC only; there are no scores");
            }
        }

        QcncAssessment assessment = assessmentRepository
                .findByFacilityIdAndAnnualCycleIdAndAdministrationType(facilityId, cycleId, administrationType)
                .orElseGet(() -> {
                    QcncAssessment created = new QcncAssessment();
                    created.setFacilityId(facilityId);
                    created.setAnnualCycleId(cycleId);
                    created.setAdministrationType(administrationType);
                    return created;
                });
        assessment.setAdministeredBy(actor.getId());
        assessment = assessmentRepository.saveAndFlush(assessment);

        resultRepository.deleteByAssessmentId(assessment.getId());
        List<QcncStandardResult> replacement = new ArrayList<>();
        for (SaveResultsRequest.ResultEntry entry : request.results()) {
            QcncStandardResult result = new QcncStandardResult();
            result.setAssessmentId(assessment.getId());
            result.setStandardId(entry.standardId());
            result.setConformity(entry.conformity());
            result.setNote(entry.note());
            replacement.add(result);
        }
        resultRepository.saveAll(replacement);

        auditService.success(actor.getId(), "SAVE_QCNC_RESULTS", "facility", facilityId,
                Map.of("cycleId", cycleId, "administrationType", administrationType.name(),
                        "results", request.results().size()));
        return getBoard(facilityId, actor);
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(u -> u.getFullName()).orElse(null);
    }
}
