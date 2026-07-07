package eu.dtc4eu.terna.entity;

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
 * A terna: three facilities visiting each other within one annual cycle
 * (Module 2). Founding/final dates live here; the three visit dates in
 * {@link TernaVisitDate}.
 */
@Entity
@Table(name = "ternas")
@Getter
@Setter
@NoArgsConstructor
public class Terna {

    public enum TernaType { TCS, GAS, SHS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terna_code", nullable = false, length = 20)
    private String ternaCode;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terna_type", nullable = false, length = 10)
    private TernaType ternaType;

    @Column(name = "founding_meeting_date")
    private LocalDate foundingMeetingDate;

    @Column(name = "final_forum_date")
    private LocalDate finalForumDate;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 2 coordinators for TCS ternas, 1 for GAS/SHS (spec T3). */
    public int requiredCoordinators() {
        return ternaType == TernaType.TCS ? 2 : 1;
    }
}
