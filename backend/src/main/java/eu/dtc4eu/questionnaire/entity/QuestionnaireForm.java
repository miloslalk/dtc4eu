package eu.dtc4eu.questionnaire.entity;

import eu.dtc4eu.facility.entity.Facility;
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

/** Administration form: how and to whom a questionnaire is administered. */
@Entity
@Table(name = "questionnaire_forms")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireForm {

    public enum AdministrationMode { INDIVIDUAL, SELFEVAL_COLLECTIVE, HETEROEVAL_DELEGATION }

    public enum StakeholderCategory { USERS, FAMILIARS, PRACTITIONERS, MANAGERS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "questionnaire_id", nullable = false)
    private Long questionnaireId;

    @Enumerated(EnumType.STRING)
    @Column(name = "administration_mode", nullable = false, length = 40)
    private AdministrationMode administrationMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "stakeholder_category", length = 20)
    private StakeholderCategory stakeholderCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", length = 10)
    private Facility.FacilityType facilityType;

    @Column(length = 150)
    private String name;
}
