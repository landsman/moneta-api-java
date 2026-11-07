package peek;

import java.util.Set;
import java.util.function.UnaryOperator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Which values in a movement could name a person: everything under the parties, any key ending in {@code name}, and
 * the keys below wherever they are. By where a value is, not by a list of known keys, so a key MONETA adds under the
 * parties is covered, in an object or in an array. {@code peek} masks these; a fixture may hold only fixed values
 * there ({@link Anonymiser}).
 */
final class Masking {

    /** MONETA's own text where the payer's message goes while an instant payment is pending; it names nobody. */
    static final String INSTANT_PLACEHOLDER = "Okamžitá platba";

    /**
     * The payment symbols (a variable or specific symbol is often a birth number or a phone) and the ids that repeat
     * them, the message, an IBAN, and the reference, which carries the account once booked.
     */
    private static final Set<String> SENSITIVE = Set.of(
            "entryReference",
            "unstructured",
            "iban",
            "reference",
            "endToEndIdentification",
            "paymentInformationIdentification");

    /** A copy with people masked: {@code <12 chars>}, or with {@code shape} letters as x and digits as 9. */
    static JsonNode mask(JsonNode movement, boolean shape) {
        var copy = movement.deepCopy();
        visit(copy, text -> text.equals(INSTANT_PLACEHOLDER) ? text : hide(text, shape));
        return copy;
    }

    /** Replaces each sensitive string in place with what {@code replace} returns for it. */
    static void visit(JsonNode node, UnaryOperator<String> replace) {
        walk(node, false, false, replace);
    }

    private static void walk(JsonNode node, boolean underParties, boolean sensitive, UnaryOperator<String> replace) {
        if (node instanceof ObjectNode object) {
            for (var name : object.propertyNames()) {
                var value = object.get(name);
                boolean parties = underParties || name.equals("relatedParties");
                boolean hidden = parties
                        || sensitive
                        || SENSITIVE.contains(name)
                        || name.toLowerCase().endsWith("name");
                if (value.isString() && hidden) {
                    object.set(name, StringNode.valueOf(replace.apply(value.asString())));
                } else {
                    walk(value, parties, hidden, replace);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                var element = array.get(i);
                if (element.isString() && (underParties || sensitive)) {
                    array.set(i, StringNode.valueOf(replace.apply(element.asString())));
                } else {
                    walk(element, underParties, sensitive, replace);
                }
            }
        }
    }

    static String hide(String text, boolean shape) {
        return shape ? text.replaceAll("\\p{L}", "x").replaceAll("\\d", "9") : "<" + text.length() + " chars>";
    }

    private Masking() {}
}
