package eu.dtc4eu.cycle.service;

import eu.dtc4eu.audit.AuditService;
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
        AnnualCycle cycle = new AnnualCycle();
        cycle.setCode(request.code());
        cycle.setStartDate(request.startDate());
        cycle.setEndDate(request.endDate());
        cycle.setSequenceNumber(request.sequenceNumber());
        cycle = cycleRepository.save(cycle);
        auditService.success(actor.getId(), "CREATE", "annual_cycle", cycle.getId());
        return CycleDto.of(cycle);
    }

    @Transactional
    public CycleDto update(Long id, UpdateCycleRequest request, AppUserDetails actor) {
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
        if (request.sequenceNumber() != null) {
            cycle.setSequenceNumber(request.sequenceNumber());
        }
        auditService.success(actor.getId(), "UPDATE", "annual_cycle", cycle.getId());
        return CycleDto.of(cycle);
    }

    private AnnualCycle find(Long id) {
        return cycleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Cycle", id));
    }
}
