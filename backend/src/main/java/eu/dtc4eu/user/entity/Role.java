package eu.dtc4eu.user.entity;

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
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    /** Role codes seeded by V2 (see schema comment on the roles table) + V9 (spec v0.2 register §9). */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String RESEARCH_TEAM = "RESEARCH_TEAM";
    public static final String UPPER_COORDINATOR = "UPPER_COORDINATOR";
    public static final String COORDINATOR = "COORDINATOR";
    public static final String AUDIT_COORDINATOR = "AUDIT_COORDINATOR";
    public static final String INTERNAL_FACILITATOR = "INTERNAL_FACILITATOR";
    public static final String EVALUATOR = "EVALUATOR";
    public static final String RESPONDENT = "RESPONDENT";
    public static final String TRAINER = "TRAINER";
    public static final String DTC_REPRESENTATIVE = "DTC_REPRESENTATIVE";
    public static final String POLICYMAKER = "POLICYMAKER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;
}
