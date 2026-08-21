package eu.dtc4eu.qualityaudit.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.qualityaudit.dto.AuditDtos;
import eu.dtc4eu.qualityaudit.entity.Audit;
import eu.dtc4eu.qualityaudit.entity.AuditAccreditedArea;
import eu.dtc4eu.qualityaudit.entity.AuditAccreditedItem;
import eu.dtc4eu.qualityaudit.entity.AuditAccreditedProcess;
import eu.dtc4eu.qualityaudit.entity.AuditCoordinator;
import eu.dtc4eu.qualityaudit.entity.AuditEvaluator;
import eu.dtc4eu.qualityaudit.repository.AuditAccreditedAreaRepository;
import eu.dtc4eu.qualityaudit.repository.AuditAccreditedItemRepository;
import eu.dtc4eu.qualityaudit.repository.AuditAccreditedProcessRepository;
import eu.dtc4eu.qualityaudit.repository.AuditCoordinatorRepository;
import eu.dtc4eu.qualityaudit.repository.AuditEvaluatorRepository;
import eu.dtc4eu.qualityaudit.repository.AuditRepository;
import eu.dtc4eu.questionnaire.entity.QuestionnaireArea;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.repository.QuestionnaireAreaRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScope;
import eu.dtc4eu.security.DataScopeService;
import eu.dtc4eu.user.entity.Role;
import eu.dtc4eu.user.entity.User;
import eu.dtc4eu.user.entity.UserFacilityAccess;
import eu.dtc4eu.user.repository.UserFacilityAccessRepository;
import eu.dtc4eu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module 3 — quality audits over the V1 §5 tables. One facility per audit,
 * typed SIMPLE / BQ / CGQ; CGQ carries up to three expert evaluators, the
 * other types one (V1 comment, enforced here). Assigned audit coordinators
 * automatically receive user_facility_access for the audited facility
 * (mirroring the terna-coordinator auto-grant); the grant is revoked when
 * they no longer coordinate any audit of that facility.
 */
@Service
@RequiredArgsConstructor
public class QualityAuditService {

    private final AuditRepository auditRepository;
    private final AuditCoordinatorRepository coordinatorRepository;
    private final AuditEvaluatorRepository evaluatorRepository;
    private final AuditAccreditedProcessRepository processRepository;
    private final AuditAccreditedAreaRepository areaRepository;
    private final AuditAccreditedItemRepository itemRepository;
    private final FacilityRepository facilityRepository;
    private final QuestionnaireAreaRepository questionnaireAreaRepository;
    private final QuestionnaireItemRepository questionnaireItemRepository;
    private final UserRepository userRepository;
    private final UserFacilityAccessRepository facilityAccessRepository;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;
    private final AuditService auditLog;

    /* ---------------------------------- reads ---------------------------------- */

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditDto> list(AppUserDetails principal) {
        Long cycleId = cycleContext.requireId();
        DataScope scope = dataScopeService.scopeOf(principal);
        List<Audit> audits;
        if (scope.all()) {
            audits = auditRepository.findByAnnualCycleIdOrderByAuditCodeAsc(cycleId);
        } else if (!scope.facilityIds().isEmpty()) {
            audits = auditRepository.findByAnnualCycleIdAndFacilityIdInOrderByAuditCodeAsc(cycleId,
                    scope.facilityIds());
        } else {
            audits = List.of();
        }
        return audits.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditDto> listForFacility(Long facilityId, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        if (!scope.all() && !scope.facilityIds().contains(facilityId)) {
            throw NotFoundException.of("Facility", facilityId);
        }
        return auditRepository.findByAnnualCycleIdAndFacilityIdOrderByAuditCodeAsc(cycleContext.requireId(),
                facilityId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AuditDtos.AuditDto get(Long id, AppUserDetails principal) {
        return toDto(findInScope(id, principal));
    }

    /* ---------------------------------- writes --------------------------------- */

    @Transactional
    public AuditDtos.AuditDto create(AuditDtos.SaveAuditRequest request, AppUserDetails actor) {
        Long cycleId = cycleContext.requireWritable().getId();
        if (auditRepository.existsByAuditCodeAndAnnualCycleId(request.auditCode(), cycleId)) {
            throw new ConflictException("Audit code already exists in this cycle");
        }
        Audit audit = new Audit();
        audit.setAnnualCycleId(cycleId);
        applyAndValidate(audit, request);
        audit = auditRepository.save(audit);
        saveAssignments(audit, request, Set.of());
        auditLog.success(actor.getId(), "CREATE", "audit", audit.getId(),
                Map.of("auditCode", audit.getAuditCode()));
        return toDto(audit);
    }

    @Transactional
    public AuditDtos.AuditDto update(Long id, AuditDtos.SaveAuditRequest request, AppUserDetails actor) {
        Audit audit = findInScope(id, actor);
        cycleContext.requireWritable();
        if (!audit.getAuditCode().equals(request.auditCode())
                && auditRepository.existsByAuditCodeAndAnnualCycleId(request.auditCode(),
                        audit.getAnnualCycleId())) {
            throw new ConflictException("Audit code already exists in this cycle");
        }
        Set<Long> previousCoordinators = coordinatorRepository.findByAuditId(audit.getId()).stream()
                .map(AuditCoordinator::getCoordinatorId).collect(Collectors.toSet());
        Long previousFacility = audit.getFacilityId();

        applyAndValidate(audit, request);
        coordinatorRepository.deleteByAuditId(audit.getId());
        evaluatorRepository.deleteByAuditId(audit.getId());
        saveAssignments(audit, request, previousCoordinators);

        // facility changed → re-evaluate old-facility grants of ALL previous coordinators
        if (!Objects.equals(previousFacility, audit.getFacilityId())) {
            for (Long userId : previousCoordinators) {
                revokeIfUnused(userId, previousFacility);
            }
        }
        auditLog.success(actor.getId(), "UPDATE", "audit", audit.getId(), Map.of());
        return toDto(audit);
    }

    @Transactional
    public void delete(Long id, AppUserDetails actor) {
        Audit audit = findInScope(id, actor);
        cycleContext.requireWritable();
        Set<Long> coordinators = coordinatorRepository.findByAuditId(id).stream()
                .map(AuditCoordinator::getCoordinatorId).collect(Collectors.toSet());
        auditRepository.delete(audit);
        auditRepository.flush();
        for (Long userId : coordinators) {
            revokeIfUnused(userId, audit.getFacilityId());
        }
        auditLog.success(actor.getId(), "DELETE", "audit", id, Map.of());
    }

    /** Replaces the accredited outcomes (processes / areas / items) atomically. */
    @Transactional
    public AuditDtos.AuditDto saveOutcomes(Long id, AuditDtos.SaveOutcomesRequest request, AppUserDetails actor) {
        Audit audit = findInScope(id, actor);
        cycleContext.requireWritable();

        for (Long areaId : request.areaIds()) {
            if (!questionnaireAreaRepository.existsById(areaId)) {
                throw new BadRequestException("Unknown questionnaire area " + areaId);
            }
        }
        for (AuditDtos.SaveOutcomesRequest.OutcomeItem item : request.items()) {
            if (!questionnaireItemRepository.existsById(item.itemId())) {
                throw new BadRequestException("Unknown questionnaire item " + item.itemId());
            }
        }

        processRepository.deleteByAuditId(id);
        areaRepository.deleteByAuditId(id);
        itemRepository.deleteByAuditId(id);

        for (String code : new LinkedHashSet<>(request.processCodes())) {
            AuditAccreditedProcess process = new AuditAccreditedProcess();
            process.setAuditId(id);
            process.setProcessCode(code);
            processRepository.save(process);
        }
        for (Long areaId : new LinkedHashSet<>(request.areaIds())) {
            AuditAccreditedArea area = new AuditAccreditedArea();
            area.setAuditId(id);
            area.setAreaId(areaId);
            areaRepository.save(area);
        }
        Set<Long> seenItems = new LinkedHashSet<>();
        for (AuditDtos.SaveOutcomesRequest.OutcomeItem outcome : request.items()) {
            if (!seenItems.add(outcome.itemId())) {
                continue;
            }
            AuditAccreditedItem item = new AuditAccreditedItem();
            item.setAuditId(id);
            item.setItemId(outcome.itemId());
            item.setExcellencePoints(outcome.excellencePoints());
            itemRepository.save(item);
        }
        auditLog.success(actor.getId(), "SAVE_AUDIT_OUTCOMES", "audit", id,
                Map.of("processes", request.processCodes().size(), "areas", request.areaIds().size(),
                        "items", request.items().size()));
        return toDto(audit);
    }

    /* --------------------------------- internals -------------------------------- */

    Audit findInScope(Long id, AppUserDetails principal) {
        Audit audit = auditRepository.findById(id).orElseThrow(() -> NotFoundException.of("Audit", id));
        if (!Objects.equals(audit.getAnnualCycleId(), cycleContext.requireId())) {
            throw NotFoundException.of("Audit", id);
        }
        DataScope scope = dataScopeService.scopeOf(principal);
        if (!scope.all() && !scope.facilityIds().contains(audit.getFacilityId())) {
            throw NotFoundException.of("Audit", id);
        }
        return audit;
    }

    /** AUDIT_COORDINATOR may edit outcomes only on audits they coordinate. */
    public void requireOutcomeWriter(Long auditId, AppUserDetails principal) {
        if (principal.hasRole(Role.ADMIN) || principal.hasRole(Role.SUPER_ADMIN)) {
            return;
        }
        boolean coordinates = coordinatorRepository.findByAuditId(auditId).stream()
                .anyMatch(c -> Objects.equals(c.getCoordinatorId(), principal.getId()));
        if (!coordinates) {
            throw NotFoundException.of("Audit", auditId);
        }
    }

    private void applyAndValidate(Audit audit, AuditDtos.SaveAuditRequest request) {
        Facility facility = facilityRepository.findById(request.facilityId())
                .orElseThrow(() -> NotFoundException.of("Facility", request.facilityId()));
        int maxEvaluators = request.auditType() == Audit.AuditType.CGQ ? 3 : 1;
        List<Long> evaluators = request.evaluatorIds().stream().distinct().toList();
        if (evaluators.size() > maxEvaluators) {
            throw new BadRequestException(request.auditType() + " audits allow at most "
                    + maxEvaluators + " expert evaluator(s)");
        }
        for (Long userId : request.coordinatorIds()) {
            if (!userRepository.existsById(userId)) {
                throw new NotFoundException("Coordinator user not found: " + userId);
            }
        }
        for (Long userId : evaluators) {
            if (!userRepository.existsById(userId)) {
                throw new NotFoundException("Evaluator user not found: " + userId);
            }
        }
        audit.setAuditCode(request.auditCode());
        audit.setFacilityId(facility.getId());
        audit.setAuditType(request.auditType());
        audit.setRenewalNumber(request.renewalNumber());
    }

    private void saveAssignments(Audit audit, AuditDtos.SaveAuditRequest request,
                                 Set<Long> previousCoordinators) {
        Set<Long> coordinators = new LinkedHashSet<>(request.coordinatorIds());
        for (Long userId : coordinators) {
            AuditCoordinator coordinator = new AuditCoordinator();
            coordinator.setAuditId(audit.getId());
            coordinator.setCoordinatorId(userId);
            coordinatorRepository.save(coordinator);
            // auto-grant facility scope (idempotent upsert)
            UserFacilityAccess.Key key = new UserFacilityAccess.Key(userId, audit.getFacilityId());
            if (!facilityAccessRepository.existsById(key)) {
                UserFacilityAccess access = new UserFacilityAccess();
                access.setUserId(userId);
                access.setFacilityId(audit.getFacilityId());
                facilityAccessRepository.save(access);
            }
        }
        coordinatorRepository.flush();
        Set<Long> removed = previousCoordinators.stream()
                .filter(userId -> !coordinators.contains(userId))
                .collect(Collectors.toSet());
        for (Long userId : removed) {
            revokeIfUnused(userId, audit.getFacilityId());
        }

        List<Long> evaluators = request.evaluatorIds().stream().distinct().toList();
        for (int i = 0; i < evaluators.size(); i++) {
            AuditEvaluator evaluator = new AuditEvaluator();
            evaluator.setAuditId(audit.getId());
            evaluator.setEvaluatorId(evaluators.get(i));
            evaluator.setEvaluatorOrder(i + 1);
            evaluatorRepository.save(evaluator);
        }
    }

    /**
     * Revokes the auto-granted facility access unless the user still
     * coordinates another audit of the same facility. (Manual grants made for
     * other reasons share the same table and would be revoked too — known
     * limitation shared with the terna-coordinator pattern.)
     */
    private void revokeIfUnused(Long userId, Long facilityId) {
        if (coordinatorRepository.countForFacility(userId, facilityId) == 0) {
            facilityAccessRepository.deleteById(new UserFacilityAccess.Key(userId, facilityId));
        }
    }

    private AuditDtos.AuditDto toDto(Audit audit) {
        Facility facility = facilityRepository.findById(audit.getFacilityId()).orElse(null);

        Function<List<Long>, Map<Long, String>> namesOf = ids -> userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        List<AuditCoordinator> coordinators = coordinatorRepository.findByAuditId(audit.getId());
        Map<Long, String> coordinatorNames = namesOf.apply(
                coordinators.stream().map(AuditCoordinator::getCoordinatorId).toList());
        List<AuditEvaluator> evaluators = evaluatorRepository.findByAuditId(audit.getId());
        Map<Long, String> evaluatorNames = namesOf.apply(
                evaluators.stream().map(AuditEvaluator::getEvaluatorId).toList());

        List<AuditAccreditedArea> areas = areaRepository.findByAuditId(audit.getId());
        Map<Long, QuestionnaireArea> areaById = questionnaireAreaRepository
                .findAllById(areas.stream().map(AuditAccreditedArea::getAreaId).toList()).stream()
                .collect(Collectors.toMap(QuestionnaireArea::getId, a -> a));
        List<AuditAccreditedItem> items = itemRepository.findByAuditId(audit.getId());
        Map<Long, QuestionnaireItem> itemById = questionnaireItemRepository
                .findAllById(items.stream().map(AuditAccreditedItem::getItemId).toList()).stream()
                .collect(Collectors.toMap(QuestionnaireItem::getId, i -> i));

        return new AuditDtos.AuditDto(
                audit.getId(), audit.getAuditCode(), audit.getAuditType(), audit.getRenewalNumber(),
                audit.getFacilityId(),
                facility != null ? facility.getFacilityCode() : null,
                facility != null ? facility.getName() : null,
                coordinators.stream().map(c -> new AuditDtos.PersonDto(c.getCoordinatorId(),
                        coordinatorNames.get(c.getCoordinatorId()))).toList(),
                evaluators.stream().map(e -> new AuditDtos.PersonDto(e.getEvaluatorId(),
                        evaluatorNames.get(e.getEvaluatorId()))).toList(),
                processRepository.findByAuditId(audit.getId()).stream()
                        .map(AuditAccreditedProcess::getProcessCode).toList(),
                areas.stream().map(a -> {
                    QuestionnaireArea area = areaById.get(a.getAreaId());
                    return new AuditDtos.AccreditedAreaDto(a.getAreaId(),
                            area != null ? area.getAreaCode() : "?",
                            area != null ? area.getName() : "?");
                }).toList(),
                items.stream().map(i -> {
                    QuestionnaireItem item = itemById.get(i.getItemId());
                    return new AuditDtos.AccreditedItemDto(i.getItemId(),
                            item != null ? item.getItemCode() : "?",
                            item != null ? item.getItemText() : "?",
                            i.getExcellencePoints());
                }).toList());
    }
}
