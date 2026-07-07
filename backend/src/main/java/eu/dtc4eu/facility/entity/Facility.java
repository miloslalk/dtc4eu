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

import java.time.OffsetDateTime;

/** The anchor entity of the platform (spec golden rule). Identified by a pseudonymous code. */
@Entity
@Table(name = "facilities")
@Getter
@Setter
@NoArgsConstructor
public class Facility {

    public enum FacilityType { TC, GA, SH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_code", nullable = false, unique = true, length = 40)
    private String facilityCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", nullable = false, length = 10)
    private FacilityType facilityType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 255)
    private String provider;

    @Column(length = 200)
    private String place;

    @Column(length = 80)
    private String country;

    @Column(length = 120)
    private String city;

    @Column
    private String address;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
