package eu.dtc4eu.assignment;

import eu.dtc4eu.assignment.service.RuleConditions;
import eu.dtc4eu.assignment.service.RuleConditions.Field;
import eu.dtc4eu.common.error.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The condition language of the Module 5-bis rules (spec COL-01): parse, validate, evaluate. */
class RuleConditionsTest {

    private static Map<String, Object> condition(String field, String operator, Object... values) {
        return Map.of("type", "condition", "field", field, "operator", operator, "values", List.of(values));
    }

    private static Map<String, Object> group(String op, Object... children) {
        return Map.of("type", "group", "op", op, "children", List.of(children));
    }

    private static boolean matches(Map<String, Object> tree, Map<Field, List<String>> context) {
        return RuleConditions.matches(RuleConditions.parse(tree), context);
    }

    @Test
    void andOrGroupsNest() {
        // "If Role = USER AND (Country = TR OR Phase = VISITING_TERNA)"
        Map<String, Object> tree = group("AND",
                condition("CATEGORY", "IN", "USER"),
                group("OR", condition("COUNTRY", "IN", "Turkey"), condition("PROJECT_PHASE", "IN", "VISITING_TERNA")));

        assertThat(matches(tree, Map.of(Field.CATEGORY, List.of("USER"), Field.COUNTRY, List.of("turkey")))).isTrue();
        assertThat(matches(tree, Map.of(Field.CATEGORY, List.of("USER"),
                Field.PROJECT_PHASE, List.of("VISITING_TERNA")))).isTrue();
        assertThat(matches(tree, Map.of(Field.CATEGORY, List.of("USER"), Field.COUNTRY, List.of("Poland")))).isFalse();
        assertThat(matches(tree, Map.of(Field.CATEGORY, List.of("STAFF"), Field.COUNTRY, List.of("Turkey")))).isFalse();
    }

    @Test
    void inMatchesAnyOfSeveralContextValuesAndNotInNeedsNone() {
        Map<Field, List<String>> roles = Map.of(Field.ROLE, List.of("COORDINATOR", "RESPONDENT"));
        assertThat(matches(condition("ROLE", "IN", "RESPONDENT"), roles)).isTrue();
        assertThat(matches(condition("ROLE", "NOT_IN", "RESPONDENT"), roles)).isFalse();
        // an absent field: IN fails, NOT_IN holds
        assertThat(matches(condition("COUNTRY", "IN", "Italy"), Map.of())).isFalse();
        assertThat(matches(condition("COUNTRY", "NOT_IN", "Italy"), Map.of())).isTrue();
    }

    @Test
    void numericBoundsAreInclusiveAndNeedAValue() {
        Map<String, Object> adult = condition("AGE", "GTE", "18");
        assertThat(matches(adult, Map.of(Field.AGE, List.of("18")))).isTrue();
        assertThat(matches(adult, Map.of(Field.AGE, List.of("17")))).isFalse();
        assertThat(matches(adult, Map.of())).isFalse();
        assertThat(matches(condition("AGE", "LTE", 65), Map.of(Field.AGE, List.of("65")))).isTrue();
    }

    @Test
    void invalidTreesAreRejectedWithTheirPath() {
        assertThatThrownBy(() -> RuleConditions.parse(group("AND")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("conditions.children");
        assertThatThrownBy(() -> RuleConditions.parse(group("AND", condition("CATEGORY", "GTE", "1"))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("conditions.children[0].operator");
        assertThatThrownBy(() -> RuleConditions.parse(condition("AGE", "GTE", "old")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("exactly one number");
        assertThatThrownBy(() -> RuleConditions.parse(condition("SHOE_SIZE", "IN", "42")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("unknown value SHOE_SIZE");
        assertThatThrownBy(() -> RuleConditions.parse(Map.of("type", "maybe")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("conditions.type");
        assertThatThrownBy(() -> RuleConditions.parse(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void storedShapeIsCanonical() {
        Map<String, Object> stored = RuleConditions.toMap(RuleConditions.parse(
                group("OR", condition("CATEGORY", "IN", " USER "))));
        assertThat(stored).containsEntry("type", "group").containsEntry("op", "OR");
        Map<?, ?> child = (Map<?, ?>) ((List<?>) stored.get("children")).get(0);
        assertThat(child.get("values")).isEqualTo(List.of("USER"));
    }
}
