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

@Entity
@Table(name = "audit_evaluators")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvaluator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audit_id", nullable = false)
    private Long auditId;

    @Column(name = "evaluator_id", nullable = false)
    private Long evaluatorId;

    @Column(name = "evaluator_order")
    private Integer evaluatorOrder;
}
