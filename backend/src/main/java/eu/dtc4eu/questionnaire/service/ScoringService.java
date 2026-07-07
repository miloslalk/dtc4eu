package eu.dtc4eu.questionnaire.service;

import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponseItem;
import eu.dtc4eu.questionnaire.entity.ResponseAreaScore;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseItemRepository;
import eu.dtc4eu.questionnaire.repository.ResponseAreaScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The scoring engine (spec T5): on submit, compute per area the mean of that
 * area's numeric LIKERT item values and write response_area_scores. Raw items
 * stay untouched; recomputation deletes and rewrites the derived rows, so the
 * method is idempotent and re-runnable.
 */
@Service
@RequiredArgsConstructor
public class ScoringService {

    private final QuestionnaireItemRepository itemRepository;
    private final QuestionnaireResponseItemRepository responseItemRepository;
    private final ResponseAreaScoreRepository scoreRepository;

    @Transactional
    public List<ResponseAreaScore> computeScores(Long responseId, Long questionnaireId) {
        List<QuestionnaireItem> definitionItems =
                itemRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(questionnaireId);
        List<QuestionnaireResponseItem> answers = responseItemRepository.findByResponseId(responseId);

        Map<Long, Integer> valueByItem = new HashMap<>();
        for (QuestionnaireResponseItem answer : answers) {
            if (answer.getNumericValue() != null) {
                valueByItem.put(answer.getItemId(), answer.getNumericValue());
            }
        }

        // area -> answered numeric values of its LIKERT items
        Map<Long, List<Integer>> valuesByArea = new HashMap<>();
        for (QuestionnaireItem item : definitionItems) {
            if (item.getResponseType() != QuestionnaireItem.ResponseType.LIKERT || item.getAreaId() == null) {
                continue;
            }
            Integer value = valueByItem.get(item.getId());
            if (value != null) {
                valuesByArea.computeIfAbsent(item.getAreaId(), k -> new ArrayList<>()).add(value);
            }
        }

        scoreRepository.deleteByResponseId(responseId);

        OffsetDateTime now = OffsetDateTime.now();
        List<ResponseAreaScore> scores = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> entry : valuesByArea.entrySet()) {
            ResponseAreaScore score = new ResponseAreaScore();
            score.setResponseId(responseId);
            score.setAreaId(entry.getKey());
            score.setMeanValue(mean(entry.getValue()));
            score.setComputedAt(now);
            scores.add(score);
        }
        return scoreRepository.saveAll(scores);
    }

    static BigDecimal mean(List<Integer> values) {
        long sum = 0;
        for (Integer value : values) {
            sum += value;
        }
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(values.size()), 3, RoundingMode.HALF_UP);
    }
}
