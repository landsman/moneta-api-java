package peek;

import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * What {@code peek} prints: a movement with every value that could name a person masked. Masked by where it is,
 * not by a list of known keys, so a key MONETA adds under the parties is masked too.
 */
final class Masking {

    /** MONETA's own text where the payer's message goes while an instant payment is pending; it names nobody. */
    static final String INSTANT_PLACEHOLDER = "Okamžitá platba";

    private static final Set<String> SENSITIVE = Set.of("entryReference", "unstructured", "iban");

    /** A copy with people masked: {@code <12 chars>}, or with {@code shape} letters as x and digits as 9. */
    static JsonNode mask(JsonNode movement, boolean shape) {
        var copy = movement.deepCopy();
        walk(copy, false, shape);
        return copy;
    }

    private static void walk(JsonNode node, boolean underParties, boolean shape) {
        if (!(node instanceof ObjectNode object)) {
            node.forEach(child -> walk(child, underParties, shape));
            return;
        }
        for (var name : object.propertyNames()) {
            var value = object.get(name);
            boolean parties = underParties || name.equals("relatedParties");
            if (value.isString()
                    && (parties
                            || SENSITIVE.contains(name)
                            || name.toLowerCase().endsWith("name"))) {
                var text = value.asString();
                if (!text.equals(INSTANT_PLACEHOLDER)) {
                    object.set(name, StringNode.valueOf(hide(text, shape)));
                }
            } else {
                walk(value, parties, shape);
            }
        }
    }

    static String hide(String text, boolean shape) {
        return shape ? text.replaceAll("\\p{L}", "x").replaceAll("\\d", "9") : "<" + text.length() + " chars>";
    }

    private Masking() {}
}
