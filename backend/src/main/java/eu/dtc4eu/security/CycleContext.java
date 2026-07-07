package eu.dtc4eu.security;

import eu.dtc4eu.common.error.BadRequestException;
import eu.dtc4eu.common.error.ConflictException;
import eu.dtc4eu.cycle.entity.AnnualCycle;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

/**
 * The annual cycle the request operates on (spec §6): X-Cycle-Id header or
 * cycleId query param, defaulting to the platform's current cycle. Populated
 * by {@link CycleInterceptor}; cycle-scoped services read it from here.
 */
@Component
@RequestScope
public class CycleContext {

    private AnnualCycle cycle;

    void set(AnnualCycle cycle) {
        this.cycle = cycle;
    }

    public Optional<AnnualCycle> maybe() {
        return Optional.ofNullable(cycle);
    }

    /** The selected cycle, or 400 when none is selected and no current cycle exists. */
    public AnnualCycle require() {
        if (cycle == null) {
            throw new BadRequestException("No annual cycle selected and no current cycle is configured");
        }
        return cycle;
    }

    public Long requireId() {
        return require().getId();
    }

    /** The selected cycle, guaranteed writable — CLOSED/ARCHIVED cycles reject writes (spec §6). */
    public AnnualCycle requireWritable() {
        AnnualCycle selected = require();
        if (!selected.isWritable()) {
            throw new ConflictException("Cycle " + selected.getCode() + " is " + selected.getStatus()
                    + " and read-only");
        }
        return selected;
    }
}
