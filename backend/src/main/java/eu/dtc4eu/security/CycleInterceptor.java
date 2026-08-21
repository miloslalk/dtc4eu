package eu.dtc4eu.security;

import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.cycle.entity.AnnualCycle;
import eu.dtc4eu.cycle.repository.AnnualCycleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class CycleInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Cycle-Id";
    public static final String PARAM = "cycleId";

    private final AnnualCycleRepository cycleRepository;
    private final CycleContext cycleContext; // request-scoped proxy

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String raw = request.getHeader(HEADER);
        if (raw == null || raw.isBlank()) {
            raw = request.getParameter(PARAM);
        }
        if (raw != null && !raw.isBlank()) {
            long cycleId;
            try {
                cycleId = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid " + HEADER + " value: " + raw);
            }
            AnnualCycle cycle = cycleRepository.findById(cycleId)
                    .orElseThrow(() -> new BadRequestException("Unknown cycle: " + cycleId));
            cycleContext.set(cycle);
        } else {
            // overlapping cycles have no "current" flag; fall back deterministically
            cycleRepository.findFirstByStatusOrderByStartDateDescIdDesc(AnnualCycle.Status.OPEN)
                    .ifPresent(cycleContext::set);
        }
        return true;
    }
}
