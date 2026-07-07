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
        return cycleRepository.findAll(Sort.by(Sort.Direction.DESC, "code")).stream().map(CycleDto::of).toList();
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
        // the very first cycle automatically becomes current
        cycle.setCurrent(cycleRepository.count() == 0);
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
            if (cycle.isCurrent() && request.status() == AnnualCycle.Status.ARCHIVED) {
                throw new ConflictException("The current cycle cannot be archived; set another cycle current first");
            }
            cycle.setStatus(request.status());
        }
        auditService.success(actor.getId(), "UPDATE", "annual_cycle", cycle.getId());
        return CycleDto.of(cycle);
    }

    @Transactional
    public CycleDto setCurrent(Long id, AppUserDetails actor) {
        AnnualCycle cycle = find(id);
        if (cycle.getStatus() == AnnualCycle.Status.ARCHIVED) {
            throw new ConflictException("An archived cycle cannot be made current");
        }
        cycleRepository.findByCurrentTrue().ifPresent(previous -> previous.setCurrent(false));
        cycle.setCurrent(true);
        auditService.success(actor.getId(), "SET_CURRENT", "annual_cycle", cycle.getId());
        return CycleDto.of(cycle);
    }

    private AnnualCycle find(Long id) {
        return cycleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Cycle", id));
    }
}
