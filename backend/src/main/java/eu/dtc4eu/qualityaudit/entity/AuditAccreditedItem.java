package eu.dtc4eu.qualityaudit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "audit_accredited_items")
@Getter
@Setter
@NoArgsConstructor
public class AuditAccreditedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", nullable = false)
    private Long auditId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "excellence_points")
    private BigDecimal excellencePoints;
}
