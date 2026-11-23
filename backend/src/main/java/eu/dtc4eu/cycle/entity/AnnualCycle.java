package eu.dtc4eu.cycle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Annual evaluation cycle — every data-collection record is scoped to one
 * (spec golden rule). CLOSED/ARCHIVED cycles are read-only. A cycle spans
 * 16 months (Sep 1 → Dec 31 of the following year) and ADJACENT CYCLES
 * OVERLAP (v0.2 register §8) — there is no single "current" cycle; every
 * request selects its cycle explicitly via X-Cycle-Id.
 */
@Entity
@Table(name = "annual_cycles")
@Getter
@Setter
@NoArgsConstructor
public class AnnualCycle {

    public enum Status { OPEN, CLOSED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Ordinal of the cycle in the accreditation programme; feeds the "1st 2023-24"-style label. */
    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    /** Optional admin-chosen landing cycle for first visits (V16, Simone's Q1); at most one. */
    @Column(name = "is_default", nullable = false)
    private boolean defaultCycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public boolean isWritable() {
        return status == Status.OPEN;
    }
}
