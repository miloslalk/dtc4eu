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

import java.time.LocalDate;

/** The three visit dates; founding + final forum live on the terna (5 key dates in total). */
@Entity
@Table(name = "terna_visit_dates")
@Getter
@Setter
@NoArgsConstructor
public class TernaVisitDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terna_id", nullable = false)
    private Long ternaId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "visit_order")
    private Integer visitOrder;
}
