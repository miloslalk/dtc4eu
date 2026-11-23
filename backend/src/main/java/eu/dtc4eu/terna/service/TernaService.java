package eu.dtc4eu.terna.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.common.web.PageResponse;
import eu.dtc4eu.facility.entity.Facility;
import eu.dtc4eu.facility.repository.FacilityCycleParticipationRepository;
import eu.dtc4eu.facility.repository.FacilityRepository;
import eu.dtc4eu.security.AppUserDetails;
import eu.dtc4eu.security.CycleContext;
import eu.dtc4eu.security.DataScope;
import eu.dtc4eu.security.DataScopeService;
import eu.dtc4eu.terna.dto.TernaDtos.CoordinatorDto;
import eu.dtc4eu.terna.dto.TernaDtos.SaveTernaRequest;
import eu.dtc4eu.terna.dto.TernaDtos.TernaDto;
import eu.dtc4eu.terna.dto.TernaDtos.TernaFacilityDto;
import eu.dtc4eu.terna.dto.TernaDtos.TernaRowDto;
import eu.dtc4eu.terna.dto.TernaDtos.VisitDateDto;
import eu.dtc4eu.terna.entity.Terna;
import eu.dtc4eu.terna.entity.TernaCoordinator;
import eu.dtc4eu.terna.entity.TernaFacility;
import eu.dtc4eu.terna.entity.TernaVisitDate;
import eu.dtc4eu.terna.repository.TernaCoordinatorRepository;
import eu.dtc4eu.terna.repository.TernaFacilityRepository;
import eu.dtc4eu.terna.repository.TernaRepository;
import eu.dtc4eu.terna.repository.TernaVisitDateRepository;
import eu.dtc4eu.user.entity.Role;
import eu.dtc4eu.user.entity.User;
import eu.dtc4eu.user.entity.UserTernaAccess;
import eu.dtc4eu.user.repository.UserRepository;
import eu.dtc4eu.user.repository.UserTernaAccessRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module 2. Business rules (spec T3): exactly 3 facilities per terna with
 * their internal facilitators auto-pulled from this cycle's participation;
 * 2 coordinators for TCS ternas, 1 for GAS/SHS; 5 key dates (founding +
 * 3 visits + final forum); a facility joins at most one terna per cycle.
 * Assigned coordinators automatically receive user_terna_access scope.
 */
@Service
@RequiredArgsConstructor
public class TernaService {

    private final TernaRepository ternaRepository;
    private final TernaFacilityRepository ternaFacilityRepository;
    private final TernaCoordinatorRepository ternaCoordinatorRepository;
    private final TernaVisitDateRepository visitDateRepository;
    private final FacilityRepository facilityRepository;
    private final FacilityCycleParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final UserTernaAccessRepository ternaAccessRepository;
    private final DataScopeService dataScopeService;
    private final CycleContext cycleContext;
    private final AuditService auditService;

    /* ---------------------------------- reads ---------------------------------- */

    @Transactional(readOnly = true)
    public PageResponse<TernaRowDto> list(Terna.TernaType type, Long facilityId, LocalDate dateFrom,
                                          LocalDate dateTo, Pageable pageable, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        Page<Terna> page = ternaRepository.findAll(specification(type, facilityId, dateFrom, dateTo, scope), pageable);

        List<Long> ternaIds = page.getContent().stream().map(Terna::getId).toList();
        Map<Long, List<TernaFacility>> facilitiesByTerna = ternaIds.isEmpty() ? Map.of()
                : ternaFacilityRepository.findByTernaIdIn(ternaIds).stream()
                        .collect(Collectors.groupingBy(TernaFacility::getTernaId));
        Map<Long, List<TernaCoordinator>> coordinatorsByTerna = ternaIds.isEmpty() ? Map.of()
                : ternaCoordinatorRepository.findByTernaIdIn(ternaIds).stream()
                        .collect(Collectors.groupingBy(TernaCoordinator::getTernaId));

        Set<Long> facilityIds = facilitiesByTerna.values().stream().flatMap(List::stream)
                .map(TernaFacility::getFacilityId).collect(Collectors.toSet());
        Map<Long, Facility> facilities = facilityRepository.findAllById(facilityIds).stream()
                .collect(Collectors.toMap(Facility::getId, Function.identity()));
        Set<Long> userIds = coordinatorsByTerna.values().stream().flatMap(List::stream)
                .map(TernaCoordinator::getCoordinatorId).collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, List<VisitDateDto>> visitsByTerna = visitDateRepository.findByTernaIdIn(ternaIds).stream()
                .sorted(java.util.Comparator.comparing(TernaVisitDate::getVisitOrder,
                        java.util.Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.groupingBy(TernaVisitDate::getTernaId, java.util.LinkedHashMap::new,
                        Collectors.mapping(v -> new VisitDateDto(v.getVisitOrder(), v.getVisitDate()),
                                Collectors.toList())));

        return PageResponse.of(page, terna -> new TernaRowDto(
                terna.getId(), terna.getTernaCode(), terna.getTernaType(),
                terna.getFoundingMeetingDate(), terna.getFinalForumDate(),
                facilitiesByTerna.getOrDefault(terna.getId(), List.of()).stream()
                        .sorted(java.util.Comparator.comparing(tf -> tf.getPosition() != null ? tf.getPosition() : 0))
                        .map(tf -> facilities.containsKey(tf.getFacilityId())
                                ? facilities.get(tf.getFacilityId()).getFacilityCode() : "?")
                        .toList(),
                coordinatorsByTerna.getOrDefault(terna.getId(), List.of()).stream()
                        .map(tc -> users.containsKey(tc.getCoordinatorId())
                                ? users.get(tc.getCoordinatorId()).getFullName() : "?")
                        .toList(),
                visitsByTerna.getOrDefault(terna.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public TernaDto get(Long id, AppUserDetails principal) {
        return toDto(findInScope(id, principal));
    }

    /* ---------------------------------- writes --------------------------------- */

    @Transactional
    public TernaDto create(SaveTernaRequest request, AppUserDetails actor) {
        Long cycleId = cycleContext.requireWritable().getId();
        ternaRepository.findByTernaCodeAndAnnualCycleId(request.ternaCode(), cycleId).ifPresent(existing -> {
            throw new ConflictException("Terna " + request.ternaCode() + " already exists in this cycle");
        });

        Terna terna = new Terna();
        terna.setTernaCode(request.ternaCode());
        terna.setAnnualCycleId(cycleId);
        applyAndValidate(terna, request, null);
        terna = ternaRepository.save(terna);

        saveAggregates(terna, request);
        auditService.success(actor.getId(), "CREATE", "terna", terna.getId());
        return toDto(terna);
    }

    @Transactional
    public TernaDto update(Long id, SaveTernaRequest request, AppUserDetails actor) {
        Terna terna = findInScope(id, actor);
        cycleContext.requireWritable();

        Set<Long> previousCoordinators = ternaCoordinatorRepository.findByTernaId(terna.getId()).stream()
                .map(TernaCoordinator::getCoordinatorId).collect(Collectors.toSet());

        applyAndValidate(terna, request, terna.getId());

        ternaFacilityRepository.deleteByTernaId(terna.getId());
        ternaCoordinatorRepository.deleteByTernaId(terna.getId());
        visitDateRepository.deleteByTernaId(terna.getId());
        saveAggregates(terna, request);

        // revoke auto-granted scope from unassigned coordinators
        Set<Long> removed = new HashSet<>(previousCoordinators);
        request.coordinatorIds().forEach(removed::remove);
        for (Long userId : removed) {
            ternaAccessRepository.deleteById(new UserTernaAccess.Key(userId, terna.getId()));
        }

        auditService.success(actor.getId(), "UPDATE", "terna", terna.getId());
        return toDto(terna);
    }

    /* --------------------------------- internals -------------------------------- */

    private void applyAndValidate(Terna terna, SaveTernaRequest request, Long selfId) {
        terna.setTernaType(request.ternaType());
        terna.setFoundingMeetingDate(request.foundingMeetingDate());
        terna.setFinalForumDate(request.finalForumDate());

        // exactly 3 distinct existing facilities
        List<Long> facilityIds = request.facilityIds();
        if (new HashSet<>(facilityIds).size() != 3) {
            throw new BadRequestException("A terna links exactly 3 distinct facilities");
        }
        List<Facility> facilities = facilityRepository.findAllById(facilityIds);
        if (facilities.size() != 3) {
            throw new NotFoundException("One or more facilities not found");
        }

        // a facility joins at most one terna per cycle (other cycles are fine — historical tracking)
        List<Long> cycleTernaIds = ternaRepository.findAll((root, q, cb) ->
                        cb.equal(root.get("annualCycleId"), terna.getAnnualCycleId())).stream()
                .map(Terna::getId)
                .filter(tid -> !Objects.equals(tid, selfId))
                .toList();
        if (!cycleTernaIds.isEmpty()) {
            List<TernaFacility> clashes = ternaFacilityRepository
                    .findByFacilityIdInAndTernaIdIn(facilityIds, cycleTernaIds);
            if (!clashes.isEmpty()) {
                throw new ConflictException("Facility " + clashes.get(0).getFacilityId()
                        + " already belongs to another terna in this cycle");
            }
        }

        // every facility's setting must match the terna type (TCS → TC, GAS → GA, SHS → SH)
        List<String> outsideSetting = ternaFacilityRepository
                .findFacilityCodesOutsideSetting(facilityIds, terna.requiredSetting());
        if (!outsideSetting.isEmpty()) {
            throw new BadRequestException(terna.getTernaType() + " ternas link " + terna.requiredSetting()
                    + " facilities only; not " + String.join(", ", outsideSetting));
        }

        // coordinator count by type: 2 for TCS, 1 for GAS/SHS
        List<Long> coordinatorIds = request.coordinatorIds() == null ? List.of() : request.coordinatorIds();
        int required = terna.requiredCoordinators();
        if (new HashSet<>(coordinatorIds).size() != required) {
            throw new BadRequestException(terna.getTernaType() + " ternas require exactly " + required
                    + " coordinator" + (required > 1 ? "s" : ""));
        }
        List<User> coordinators = userRepository.findAllById(coordinatorIds);
        if (coordinators.size() != coordinatorIds.size()) {
            throw new NotFoundException("One or more coordinators not found");
        }
        for (User coordinator : coordinators) {
            if (!coordinator.roleCodes().contains(Role.COORDINATOR)) {
                throw new BadRequestException("User " + coordinator.getUsername() + " does not hold the "
                        + "COORDINATOR role");
            }
        }

        // up to 3 visit dates with distinct orders 1..3
        List<VisitDateDto> visits = request.visitDates() == null ? List.of() : request.visitDates();
        Set<Integer> orders = new HashSet<>();
        for (VisitDateDto visit : visits) {
            if (visit.visitDate() == null) {
                throw new BadRequestException("Visit dates must not be empty");
            }
            Integer order = visit.visitOrder();
            if (order == null || order < 1 || order > 3 || !orders.add(order)) {
                throw new BadRequestException("Visit order must be a distinct value between 1 and 3");
            }
        }
    }

    private void saveAggregates(Terna terna, SaveTernaRequest request) {
        // facilities with auto-pulled internal facilitator from this cycle's participation (spec T3)
        List<TernaFacility> members = new ArrayList<>();
        for (int i = 0; i < request.facilityIds().size(); i++) {
            Long facilityId = request.facilityIds().get(i);
            TernaFacility member = new TernaFacility();
            member.setTernaId(terna.getId());
            member.setFacilityId(facilityId);
            member.setPosition(i + 1);
            participationRepository.findByFacilityIdAndAnnualCycleId(facilityId, terna.getAnnualCycleId())
                    .ifPresent(p -> member.setInternalFacilitatorId(p.getInternalFacilitatorId()));
            members.add(member);
        }
        ternaFacilityRepository.saveAll(members);

        List<TernaCoordinator> coordinators = request.coordinatorIds().stream().distinct().map(userId -> {
            TernaCoordinator tc = new TernaCoordinator();
            tc.setTernaId(terna.getId());
            tc.setCoordinatorId(userId);
            return tc;
        }).toList();
        ternaCoordinatorRepository.saveAll(coordinators);

        // assigned coordinators automatically gain data scope over the terna
        for (Long userId : request.coordinatorIds()) {
            if (!ternaAccessRepository.existsById(new UserTernaAccess.Key(userId, terna.getId()))) {
                ternaAccessRepository.save(new UserTernaAccess(userId, terna.getId()));
            }
        }

        if (request.visitDates() != null) {
            List<TernaVisitDate> visits = request.visitDates().stream().map(dto -> {
                TernaVisitDate visit = new TernaVisitDate();
                visit.setTernaId(terna.getId());
                visit.setVisitDate(dto.visitDate());
                visit.setVisitOrder(dto.visitOrder());
                return visit;
            }).toList();
            visitDateRepository.saveAll(visits);
        }
    }

    private Terna findInScope(Long id, AppUserDetails principal) {
        DataScope scope = dataScopeService.scopeOf(principal);
        return ternaRepository.findById(id)
                .filter(terna -> terna.getAnnualCycleId().equals(cycleContext.requireId()))
                .filter(terna -> scope.canSeeTerna(terna.getId()))
                .orElseThrow(() -> NotFoundException.of("Terna", id));
    }

    private Specification<Terna> specification(Terna.TernaType type, Long facilityId, LocalDate dateFrom,
                                               LocalDate dateTo, DataScope scope) {
        Long cycleId = cycleContext.requireId();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("annualCycleId"), cycleId));
            if (!scope.all()) {
                if (scope.ternaIds().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("id").in(scope.ternaIds()));
                }
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("ternaType"), type));
            }
            if (facilityId != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<TernaFacility> tf = sub.from(TernaFacility.class);
                sub.select(tf.get("ternaId"))
                        .where(cb.equal(tf.get("ternaId"), root.get("id")),
                                cb.equal(tf.get("facilityId"), facilityId));
                predicates.add(cb.exists(sub));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("foundingMeetingDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("foundingMeetingDate"), dateTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TernaDto toDto(Terna terna) {
        List<TernaFacility> members = ternaFacilityRepository.findByTernaIdOrderByPositionAsc(terna.getId());
        List<TernaCoordinator> coordinators = ternaCoordinatorRepository.findByTernaId(terna.getId());
        List<TernaVisitDate> visits = visitDateRepository.findByTernaIdOrderByVisitOrderAsc(terna.getId());

        Map<Long, Facility> facilities = facilityRepository.findAllById(
                        members.stream().map(TernaFacility::getFacilityId).toList()).stream()
                .collect(Collectors.toMap(Facility::getId, Function.identity()));
        Set<Long> userIds = new HashSet<>();
        coordinators.forEach(tc -> userIds.add(tc.getCoordinatorId()));
        members.forEach(m -> {
            if (m.getInternalFacilitatorId() != null) {
                userIds.add(m.getInternalFacilitatorId());
            }
        });
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return new TernaDto(terna.getId(), terna.getTernaCode(), terna.getTernaType(),
                terna.getFoundingMeetingDate(), terna.getFinalForumDate(),
                members.stream().map(m -> {
                    Facility facility = facilities.get(m.getFacilityId());
                    User facilitator = m.getInternalFacilitatorId() != null
                            ? users.get(m.getInternalFacilitatorId()) : null;
                    return new TernaFacilityDto(m.getFacilityId(),
                            facility != null ? facility.getFacilityCode() : "?",
                            facility != null ? facility.getName() : "?",
                            m.getInternalFacilitatorId(),
                            facilitator != null ? facilitator.getFullName() : null,
                            m.getPosition());
                }).toList(),
                coordinators.stream().map(tc -> new CoordinatorDto(tc.getCoordinatorId(),
                        users.containsKey(tc.getCoordinatorId())
                                ? users.get(tc.getCoordinatorId()).getFullName() : "?")).toList(),
                visits.stream().map(v -> new VisitDateDto(v.getVisitOrder(), v.getVisitDate())).toList());
    }
}
