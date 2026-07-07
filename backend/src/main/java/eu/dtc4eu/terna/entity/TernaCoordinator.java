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

@Entity
@Table(name = "terna_coordinators")
@Getter
@Setter
@NoArgsConstructor
public class TernaCoordinator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terna_id", nullable = false)
    private Long ternaId;

    @Column(name = "coordinator_id", nullable = false)
    private Long coordinatorId;
}
