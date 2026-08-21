package eu.dtc4eu.facility.service;

import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.facility.dto.QcncDtos.AdministrationDto;
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
 *
 * Meta lists per the expert feedback ("Meta-area of Strengths/weakness in
 * all three QCNC questionnaires administered") are computed at STANDARD
 * level: C in all three administrations / NC in all three. The cluster-level
 * qualitative rule for the Summary Table is still an open question with the
 * client and stays unimplemented.
 */
@Service
@RequiredArgsConstructor
public class QcncStandardsService {

    private final QcncStandardRepository standardRepository;
    private final QcncAssessmentRepository assessmentRepository;
    private final QcncStandardResultRepository resultRepository;
    private final UserRepository userRepository;
    private final FacilityService facilityService;
    private final CycleContext cycleContext;
    private final AuditService auditService;

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
            Map<Long, String> conformityByStandard = resultsByAssessment
                    .getOrDefault(assessment.getId(), List.of()).stream()
                    .collect(Collectors.toMap(QcncStandardResult::getStandardId,
                            r -> r.getConformity().name(), (a, b) -> a, LinkedHashMap::new));
            administrations.put(assessment.getAdministrationType(), new AdministrationDto(
                    assessment.getAdministrationType(),
                    assessment.getAdministeredBy(),
                    userName(assessment.getAdministeredBy()),
                    assessment.getUpdatedAt(),
                    conformityByStandard));
        }

        return new QcncBoardDto(standards, administrations.values().stream().toList(),
                metaList(administrations, "C"), metaList(administrations, "NC"));
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
