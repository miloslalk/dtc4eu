package eu.dtc4eu.questionnaire.repository;

import eu.dtc4eu.questionnaire.entity.Questionnaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionnaireRepository extends JpaRepository<Questionnaire, Long> {

    Optional<Questionnaire> findByCode(String code);

    boolean existsByCode(String code);
}
