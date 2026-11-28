package eu.dtc4eu.questionnaire.service;

import eu.dtc4eu.questionnaire.entity.Questionnaire;
import eu.dtc4eu.questionnaire.entity.QuestionnaireItem;
import eu.dtc4eu.questionnaire.entity.QuestionnaireResponseItem;
import eu.dtc4eu.questionnaire.entity.ResponseAreaScore;
import eu.dtc4eu.questionnaire.repository.QuestionnaireItemRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireRepository;
import eu.dtc4eu.questionnaire.repository.QuestionnaireResponseItemRepository;
import eu.dtc4eu.questionnaire.repository.ResponseAreaScoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    private static final long RESPONSE_ID = 100L;
    private static final long QUESTIONNAIRE_ID = 1L;

    @Mock
    private QuestionnaireRepository questionnaireRepository;

    @Mock
    private QuestionnaireItemRepository itemRepository;

    @Mock
    private QuestionnaireResponseItemRepository responseItemRepository;

    @Mock
    private ResponseAreaScoreRepository scoreRepository;

    @InjectMocks
    private ScoringService scoringService;

    @Test
    void computesTheMeanPerAreaFromNumericLikertValues() {
        // area 10: values 1,2,3 → 2.000 ; area 20: values 4,4 → 4.000
        stubDefinition(
                likertItem(1L, 10L), likertItem(2L, 10L), likertItem(3L, 10L),
                likertItem(4L, 20L), likertItem(5L, 20L));
        stubAnswers(numeric(1L, 1), numeric(2L, 2), numeric(3L, 3), numeric(4L, 4), numeric(5L, 4));
        stubSaveAll();

        Map<Long, BigDecimal> byArea = compute();

        assertThat(byArea.get(10L)).isEqualByComparingTo("2.000");
        assertThat(byArea.get(20L)).isEqualByComparingTo("4.000");
    }

    @Test
    void nonTerminatingMeansAreRoundedToThreeDecimals() {
        // 1,1,2 → 4/3 = 1.333 ; 2,3 → 2.5 → 2.500
        stubDefinition(likertItem(1L, 10L), likertItem(2L, 10L), likertItem(3L, 10L),
                likertItem(4L, 20L), likertItem(5L, 20L));
        stubAnswers(numeric(1L, 1), numeric(2L, 1), numeric(3L, 2), numeric(4L, 2), numeric(5L, 3));
        stubSaveAll();

        Map<Long, BigDecimal> byArea = compute();

        assertThat(byArea.get(10L)).isEqualByComparingTo("1.333");
        assertThat(byArea.get(20L)).isEqualByComparingTo("2.500");
    }

    @Test
    void freeTextItemsAndUnansweredItemsAreExcluded() {
        QuestionnaireItem freeText = likertItem(3L, 10L);
        freeText.setResponseType(QuestionnaireItem.ResponseType.FREE_TEXT);
        stubDefinition(likertItem(1L, 10L), likertItem(2L, 10L), freeText, likertItem(4L, 10L));

        QuestionnaireResponseItem text = new QuestionnaireResponseItem();
        text.setItemId(3L);
        text.setTextValue("free text answer");
        // item 4 unanswered
        stubAnswers(numeric(1L, 2), numeric(2L, 4), text);
        stubSaveAll();

        Map<Long, BigDecimal> byArea = compute();

        assertThat(byArea).containsOnlyKeys(10L);
        assertThat(byArea.get(10L)).isEqualByComparingTo("3.000"); // (2+4)/2
    }

    @Test
    void itemsWithoutAnAreaDoNotProduceScores() {
        stubDefinition(likertItem(1L, null), likertItem(2L, 10L));
        stubAnswers(numeric(1L, 4), numeric(2L, 2));
        stubSaveAll();

        Map<Long, BigDecimal> byArea = compute();

        assertThat(byArea).containsOnlyKeys(10L);
    }

    /** ViVaCom 8/9 (V18): answers outside the scale are recorded but not scored. */
    @Test
    void answersOutsideTheScaleAreLeftOutOfTheMean() {
        stubDefinition(likertItem(1L, 10L), likertItem(2L, 10L), likertItem(3L, 10L));
        stubAnswers(numeric(1L, 9), numeric(2L, 2), numeric(3L, 4));
        stubSaveAll();

        assertThat(compute().get(10L)).isEqualByComparingTo("3.000");
    }

    @Test
    void recomputationIsIdempotentBecauseOldScoresAreDeletedFirst() {
        stubDefinition(likertItem(1L, 10L));
        stubAnswers(numeric(1L, 3));
        stubSaveAll();

        scoringService.computeScores(RESPONSE_ID, QUESTIONNAIRE_ID);

        verify(scoreRepository).deleteByResponseId(RESPONSE_ID);
    }

    @Test
    void meanHelperIsExactForTerminatingDivisions() {
        assertThat(ScoringService.mean(List.of(1, 2, 3, 4))).isEqualByComparingTo("2.500");
        assertThat(ScoringService.mean(List.of(2, 2, 2))).isEqualByComparingTo("2.000");
        assertThat(ScoringService.mean(List.of(1, 1, 1, 2))).isEqualByComparingTo("1.250");
    }

    /* --------------------------------- helpers --------------------------------- */

    private Map<Long, BigDecimal> compute() {
        return scoringService.computeScores(RESPONSE_ID, QUESTIONNAIRE_ID).stream()
                .collect(Collectors.toMap(ResponseAreaScore::getAreaId, ResponseAreaScore::getMeanValue));
    }

    private void stubDefinition(QuestionnaireItem... items) {
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setScaleMin(1);
        questionnaire.setScaleMax(4);
        when(questionnaireRepository.findById(QUESTIONNAIRE_ID)).thenReturn(Optional.of(questionnaire));
        when(itemRepository.findByQuestionnaireIdOrderBySortOrderAscIdAsc(QUESTIONNAIRE_ID))
                .thenReturn(List.of(items));
    }

    private void stubAnswers(QuestionnaireResponseItem... answers) {
        when(responseItemRepository.findByResponseId(RESPONSE_ID)).thenReturn(List.of(answers));
    }

    private void stubSaveAll() {
        when(scoreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    private QuestionnaireItem likertItem(Long id, Long areaId) {
        QuestionnaireItem item = new QuestionnaireItem() {
            @Override
            public Long getId() {
                return id;
            }
        };
        item.setQuestionnaireId(QUESTIONNAIRE_ID);
        item.setAreaId(areaId);
        item.setItemCode("D" + id);
        item.setItemText("item " + id);
        item.setResponseType(QuestionnaireItem.ResponseType.LIKERT);
        return item;
    }

    private QuestionnaireResponseItem numeric(Long itemId, int value) {
        QuestionnaireResponseItem answer = new QuestionnaireResponseItem();
        answer.setResponseId(RESPONSE_ID);
        answer.setItemId(itemId);
        answer.setNumericValue(value);
        return answer;
    }
}
