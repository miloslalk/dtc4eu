package eu.dtc4eu.assignment.service;

import eu.dtc4eu.common.error.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The assignment rules' condition language (spec COL-01) — pure, no I/O.
 *
 * <p>A rule's JSONB tree is either a group
 * {@code {"type":"group","op":"AND"|"OR","children":[…]}} or a condition
 * {@code {"type":"condition","field":"CATEGORY","operator":"IN","values":["USER"]}}.
 * Groups nest to any depth. A context maps each {@link Field} to the values the
 * respondent has for it (several when, for example, a user holds several roles).
 */
public final class RuleConditions {

    private RuleConditions() {
    }

    /** Profile and context fields a rule can test. */
    public enum Field { CATEGORY, AGE, COUNTRY, PROJECT_PHASE, FACILITY_SETTING, FACILITY_SUBTYPE, TERNA_TYPE, CYCLE, ROLE }

    /**
     * IN: some context value is listed. NOT_IN: no context value is listed (an
     * absent field qualifies). GTE / LTE: numeric fields only, compared with the
     * single listed bound; an absent field never qualifies.
     */
    public enum Operator { IN, NOT_IN, GTE, LTE }

    public enum GroupOp { AND, OR }

    public sealed interface Node permits Group, Condition {
    }

    public record Group(GroupOp op, List<Node> children) implements Node {
    }

    public record Condition(Field field, Operator operator, List<String> values) implements Node {
    }

    private static final Set<Field> NUMERIC = Set.of(Field.AGE);

    /** Parses and validates a stored or submitted tree; any defect is a 400 naming its path. */
    public static Node parse(Map<String, Object> tree) {
        if (tree == null) {
            throw new BadRequestException("conditions: a condition tree is required");
        }
        return parse(tree, "conditions");
    }

    @SuppressWarnings("unchecked")
    private static Node parse(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new BadRequestException(path + ": must be an object");
        }
        Object type = map.get("type");
        if ("group".equals(type)) {
            GroupOp op = enumValue(GroupOp.class, map.get("op"), path + ".op");
            if (!(map.get("children") instanceof List<?> children) || children.isEmpty()) {
                throw new BadRequestException(path + ".children: a group needs at least one condition");
            }
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < children.size(); i++) {
                nodes.add(parse(children.get(i), path + ".children[" + i + "]"));
            }
            return new Group(op, List.copyOf(nodes));
        }
        if ("condition".equals(type)) {
            Field field = enumValue(Field.class, map.get("field"), path + ".field");
            Operator operator = enumValue(Operator.class, map.get("operator"), path + ".operator");
            if (!(map.get("values") instanceof List<?> rawValues) || rawValues.isEmpty()) {
                throw new BadRequestException(path + ".values: at least one value is required");
            }
            List<String> values = ((List<Object>) rawValues).stream().map(String::valueOf).map(String::trim).toList();
            boolean comparison = operator == Operator.GTE || operator == Operator.LTE;
            if (comparison && !NUMERIC.contains(field)) {
                throw new BadRequestException(path + ".operator: " + operator + " applies to numeric fields only");
            }
            if (comparison && (values.size() != 1 || parseNumber(values.get(0)) == null)) {
                throw new BadRequestException(path + ".values: " + operator + " takes exactly one number");
            }
            return new Condition(field, operator, values);
        }
        throw new BadRequestException(path + ".type: must be \"group\" or \"condition\"");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object raw, String path) {
        try {
            return Enum.valueOf(type, String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(path + ": unknown value " + raw);
        }
    }

    /** True when the context satisfies the node. Value comparison ignores case. */
    public static boolean matches(Node node, Map<Field, List<String>> context) {
        if (node instanceof Group group) {
            return group.op() == GroupOp.AND
                    ? group.children().stream().allMatch(child -> matches(child, context))
                    : group.children().stream().anyMatch(child -> matches(child, context));
        }
        Condition condition = (Condition) node;
        return matches(condition, context.getOrDefault(condition.field(), List.of()));
    }

    private static boolean matches(Condition condition, List<String> actual) {
        return switch (condition.operator()) {
            case IN -> actual.stream().anyMatch(value -> containsIgnoreCase(condition.values(), value));
            case NOT_IN -> actual.stream().noneMatch(value -> containsIgnoreCase(condition.values(), value));
            case GTE -> actual.stream().map(RuleConditions::parseNumber)
                    .anyMatch(n -> n != null && n >= parseNumber(condition.values().get(0)));
            case LTE -> actual.stream().map(RuleConditions::parseNumber)
                    .anyMatch(n -> n != null && n <= parseNumber(condition.values().get(0)));
        };
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(value));
    }

    private static Double parseNumber(String value) {
        try {
            return value == null ? null : Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Serialises a parsed tree back to the canonical JSONB shape (trimmed values, fixed key order). */
    public static Map<String, Object> toMap(Node node) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (node instanceof Group group) {
            map.put("type", "group");
            map.put("op", group.op().name());
            map.put("children", group.children().stream().map(RuleConditions::toMap).toList());
        } else {
            Condition condition = (Condition) node;
            map.put("type", "condition");
            map.put("field", condition.field().name());
            map.put("operator", condition.operator().name());
            map.put("values", condition.values());
        }
        return map;
    }
}
