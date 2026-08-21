package eu.dtc4eu.facility.entity;

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

/**
 * Facility member roster entry, per cycle. Members are pseudonymous codes
 * (Excel: 'Codes'), never personal names (spec golden rule).
 */
@Entity
@Table(name = "facility_members")
@Getter
@Setter
@NoArgsConstructor
public class FacilityMember {

    public enum Category { USER, FAMILY, STAFF, MANAGER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "annual_cycle_id", nullable = false)
    private Long annualCycleId;

    @Column(name = "member_code", nullable = false, length = 40)
    private String memberCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;
}
