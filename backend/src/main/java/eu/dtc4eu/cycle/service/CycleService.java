package eu.dtc4eu.cycle.service;

import eu.dtc4eu.audit.AuditService;
import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.common.error.NotFoundException;
import eu.dtc4eu.cycle.dto.CycleDtos.CreateCycleRequest;
import eu.dtc4eu.cycle.dto.CycleDtos.CycleDto;
import eu.dtc4eu.cycle.dto.CycleDtos.UpdateCycleRequest;
import eu.dtc4eu.cycle.entity.AnnualCycle;
import eu.dtc4eu.cycle.repository.AnnualCycleRepository;
import eu.dtc4eu.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CycleService {

    private final AnnualCycleRepository cycleRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CycleDto> list() {
        // overlapping cycles: most recently started first (the register removed the "current" flag)
        return cycleRepository.findAll(Sort.by(Sort.Direction.DESC, "startDate", "code")).stream()
                .map(CycleDto::of).toList();
    }

    @Transactional(readOnly = true)
    public CycleDto get(Long id) {
        return CycleDto.of(find(id));
    }

    @Transactional
    public CycleDto create(CreateCycleRequest request, AppUserDetails actor) {
        if (cycleRepository.existsByCode(request.code())) {
            throw new ConflictException("Cycle " + request.code() + " already exists");
        }
        int firstYear = firstYear(request.code());
        AnnualCycle cycle = new AnnualCycle();
        cycle.setCode(request.code());
        // absent dates default to the fixed 16-month window (spec REG-01)
        cycle.setStartDate(request.startDate() != null ? request.startDate() : expectedStart(firstYear));
        cycle.setEndDate(request.endDate() != null ? request.endDate() : expectedEnd(firstYear));
        requireCycleWindow(cycle);
        Long id = cycleRepository.saveAndFlush(cycle).getId();
        cycleRepository.renumberByStartDate();
        auditService.success(actor.getId(), "CREATE", "annual_cycle", id);
        return CycleDto.of(find(id));
    }

    @Transactional
    public CycleDto update(Long id, UpdateCycleRequest request, AppUserDetails actor) {
        if (Boolean.TRUE.equals(request.isDefault())) {
            // one landing default at a time; cleared before loading (the query clears the context)
            cycleRepository.clearDefaultExcept(id);
        }
        AnnualCycle cycle = find(id);
        if (request.startDate() != null) {
            cycle.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            cycle.setEndDate(request.endDate());
        }
        if (request.status() != null) {
            cycle.setStatus(request.status());
        }
        requireCycleWindow(cycle);
        if (request.isDefault() != null) {
            cycle.setDefaultCycle(request.isDefault());
        }
        cycleRepository.saveAndFlush(cycle);
        cycleRepository.renumberByStartDate();
        auditService.success(actor.getId(), "UPDATE", "annual_cycle", id);
        return CycleDto.of(find(id));
    }

    /**
     * A cycle runs from 1 September of year N to 31 December of year N+1
     * (spec REG-01), where the code is "N-(N+1)". Historical back-filled
     * cycles follow the same window.
     */
    private static void requireCycleWindow(AnnualCycle cycle) {
        int firstYear = firstYear(cycle.getCode());
        if (cycle.getStartDate() != null && !cycle.getStartDate().equals(expectedStart(firstYear))) {
            throw new BadRequestException("Cycle " + cycle.getCode() + " must start on " + expectedStart(firstYear));
        }
        if (cycle.getEndDate() != null && !cycle.getEndDate().equals(expectedEnd(firstYear))) {
            throw new BadRequestException("Cycle " + cycle.getCode() + " must end on " + expectedEnd(firstYear));
        }
    }

    private static int firstYear(String code) {
        int first = Integer.parseInt(code.substring(0, 4));
        if (Integer.parseInt(code.substring(5, 9)) != first + 1) {
            throw new BadRequestException("Cycle code must span two consecutive years, e.g. 2026-2027");
        }
        return first;
    }

    private static LocalDate expectedStart(int firstYear) {
        return LocalDate.of(firstYear, Month.SEPTEMBER, 1);
    }

    private static LocalDate expectedEnd(int firstYear) {
        return LocalDate.of(firstYear + 1, Month.DECEMBER, 31);
    }

    private AnnualCycle find(Long id) {
        return cycleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Cycle", id));
    }
}
