package peek;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Turns a live movement into a fixture by the rules of modules/moneta-api/src/test/resources/moneta/README.md: the
 * fixed test accounts, {@code Novak, Jan}, a placeholder message, a reference in MONETA's shape. A string under the
 * parties it has no rule for stops the recording rather than reaching the public repository.
 */
final class Anonymiser {

    static final String OWN_ACCOUNT = "1337/0600";
    static final String OWN_IBAN = "CZ0206000000000000001337";
    static final String OTHER_ACCOUNT = "4040404040/0800";
    static final String OTHER_IBAN = "CZ4008000000004040404040";
    static final String PERSON = "Novak, Jan";
    static final String MESSAGE = "ZPRAVA PRO PRIJEMCE";
    /** A card network's descriptor: 25 characters of name, 13 of city, 2 of country, as a live one is laid out. */
    static final String MERCHANT = "%-25s%-13s%s".formatted("PAYFAC  *MICHAL LANDSMAN", "PRAHA", "CZ");

    /** The movement as a one-item page, people replaced; {@code ownAccountId} is the account it was read from. */
    static ObjectNode fixture(ObjectNode live, String ownAccountId) {
        var movement = live.deepCopy();
        var replaced = new ArrayList<String>();
        reference(movement, replaced);
        var details = movement.path("entryDetails").path("transactionDetails");
        if (details.path("remittanceInformation") instanceof ObjectNode remittance
                && remittance.get("unstructured") instanceof JsonNode message
                && !message.asString().equals(Masking.INSTANT_PLACEHOLDER)) {
            replaced.add(message.asString());
            remittance.put("unstructured", MESSAGE);
        }
        if (details.path("relatedParties") instanceof ObjectNode parties) {
            boolean card = details.path("references")
                    .path("clearingSystemReference")
                    .asString("")
                    .endsWith("PAYMENT_CARD");
            parties(parties, "", ownAccountId, card, replaced);
        }
        var page = Peek.JSON.createObjectNode();
        page.putArray("transactions").add(movement);
        page.put("pageNumber", 0).put("pageSize", 100).put("pageCount", 1);
        leftovers(page, replaced);
        return page;
    }

    /** 19 digits pending, {@code account:bookingDate:sequence:reference} once booked; the booking date stays. */
    private static void reference(ObjectNode movement, List<String> replaced) {
        var live = movement.path("entryReference").asString();
        replaced.add(live);
        var parts = live.split(":");
        movement.put(
                "entryReference",
                parts.length == 4
                        ? "0000001337:%s:00001:26110600000000001".formatted(parts[1])
                        : "2600000000000000001");
    }

    private static void parties(ObjectNode node, String path, String own, boolean card, List<String> replaced) {
        for (var name : node.propertyNames()) {
            var value = node.get(name);
            var at = path + "." + name;
            if (value instanceof ObjectNode child) {
                parties(child, at, own, card, replaced);
                continue;
            }
            if (!value.isString()) {
                continue;
            }
            var text = value.asString();
            boolean ours = digits(text).contains(digits(own).replaceFirst("^0+", ""));
            var replacement = switch (name) {
                case "name" -> card && at.startsWith(".creditor.") ? MERCHANT : PERSON;
                case "identification" -> ours ? OWN_ACCOUNT : OTHER_ACCOUNT;
                case "iban" -> ours ? OWN_IBAN : OTHER_IBAN;
                default ->
                    throw new IllegalStateException("no rule for relatedParties" + at + "; add one to Anonymiser");
            };
            replaced.add(text);
            node.set(name, StringNode.valueOf(replacement));
        }
    }

    /** The last check: nothing replaced may still be anywhere in what gets written. */
    private static void leftovers(JsonNode page, List<String> replaced) {
        var written = page.toString();
        for (var text : replaced) {
            if (text.length() > 3 && written.contains(text) && !isFixed(text)) {
                throw new IllegalStateException("a replaced value is still in the fixture; nothing written");
            }
        }
    }

    private static boolean isFixed(String text) {
        return List.of(OWN_ACCOUNT, OWN_IBAN, OTHER_ACCOUNT, OTHER_IBAN, PERSON, MESSAGE, MERCHANT)
                .contains(text);
    }

    private static String digits(String text) {
        return text.replaceAll("\\D", "");
    }

    private Anonymiser() {}
}
