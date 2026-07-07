package eu.dtc4eu.terna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One of the exactly-three member facilities, with its internal facilitator auto-pulled at assignment. */
@Entity
@Table(name = "terna_facilities")
@Getter
@Setter
@NoArgsConstructor
public class TernaFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terna_id", nullable = false)
    private Long ternaId;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "internal_facilitator_id")
    private Long internalFacilitatorId;

    @Column
    private Integer position;
}
