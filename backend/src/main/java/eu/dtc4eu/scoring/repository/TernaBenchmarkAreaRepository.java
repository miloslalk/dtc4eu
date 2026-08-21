package eu.dtc4eu.scoring.repository;

import eu.dtc4eu.scoring.entity.TernaBenchmarkArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TernaBenchmarkAreaRepository extends JpaRepository<TernaBenchmarkArea, Long> {

    List<TernaBenchmarkArea> findByTernaIdAndQuestionnaireId(Long ternaId, Long questionnaireId);

    /** Bulk delete executing immediately — the rewrite's inserts must not flush before it. */
    @Modifying
    @Query("delete from TernaBenchmarkArea b where b.ternaId = :ternaId and b.questionnaireId = :questionnaireId")
    void deleteByTernaIdAndQuestionnaireId(@Param("ternaId") Long ternaId,
                                           @Param("questionnaireId") Long questionnaireId);
}
