package peek;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Turns a live movement into a fixture by the rules of modules/moneta-api/src/test/resources/moneta/README.md: the
 * fixed test accounts in the shape MONETA wrote them, {@code Novak, Jan}, a placeholder message, symbols 1337, 1000
 * and 76789, a reference in MONETA's shape. Two checks stop a recording rather than let a person reach the public
 * repository: a value under the parties it has no rule for, and, at the end, any value {@link Masking} would hide
 * that is not one of the fixed ones.
 */
final class Anonymiser {

    static final String OWN_ACCOUNT = "1337/0600";
    static final String OWN_IBAN = "CZ0206000000000000001337";
    static final String OTHER_ACCOUNT = "4040404040/0800";
    static final String OTHER_IBAN = "CZ4008000000004040404040";
    static final String PERSON = "Novak, Jan";
    static final String MESSAGE = "ZPRAVA PRO PRIJEMCE";
    static final String VARIABLE = "1337";
    static final String CONSTANT = "1000";
    static final String SPECIFIC = "76789";
    /** A card network's descriptor: 25 characters of name, 13 of city, 2 of country, as a live one is laid out. */
    static final String MERCHANT = "%-25s%-13s%s".formatted("PAYFAC  *MICHAL LANDSMAN", "PRAHA", "CZ");

    private static final String PENDING_REFERENCE = "2600000000000000001";
    private static final Set<String> FIXED = Set.of(
            OWN_ACCOUNT,
            "1337",
            OWN_IBAN,
            OTHER_ACCOUNT,
            "4040404040",
            "0 " + OTHER_ACCOUNT,
            "0 4040404040",
            OTHER_IBAN,
            PERSON,
            MESSAGE,
            MERCHANT,
            SPECIFIC,
            PENDING_REFERENCE,
            Masking.INSTANT_PLACEHOLDER,
            "");

    /** The movement as a one-item page, people replaced; {@code ownAccountId} is the account it was read from. */
    static ObjectNode fixture(ObjectNode live, String ownAccountId) {
        var movement = live.deepCopy();
        var replaced = new ArrayList<String>();
        reference(movement, replaced);
        var details = movement.path("entryDetails").path("transactionDetails");
        if (details.path("remittanceInformation") instanceof ObjectNode remittance) {
            if (remittance.get("unstructured") instanceof JsonNode message
                    && !message.asString().equals(Masking.INSTANT_PLACEHOLDER)) {
                replaced.add(message.asString());
                remittance.put("unstructured", MESSAGE);
            }
            if (remittance.path("structured").path("creditorReferenceInformation") instanceof ObjectNode creditor
                    && creditor.get("reference") instanceof JsonNode symbols) {
                replaced.add(symbols.asString());
                creditor.put("reference", symbols(symbols.asString()));
            }
        }
        if (details.path("references") instanceof ObjectNode references) {
            replace(references, "endToEndIdentification", VARIABLE, replaced);
            replace(references, "paymentInformationIdentification", SPECIFIC, replaced);
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
        onlyFixedWherePeekMasks(page);
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
                parts.length == 4 ? "0000001337:%s:00001:26110600000000001".formatted(parts[1]) : PENDING_REFERENCE);
    }

    /** {@code VS:… KS:… SS:…}, each number replaced and its layout kept, an empty symbol left empty. */
    static String symbols(String live) {
        return live.replaceAll("VS:\\d+", "VS:" + VARIABLE)
                .replaceAll("KS:\\d+", "KS:" + CONSTANT)
                .replaceAll("SS:\\d+", "SS:" + SPECIFIC);
    }

    private static void replace(ObjectNode node, String name, String fixed, List<String> replaced) {
        if (node.get(name) instanceof JsonNode value) {
            replaced.add(value.asString());
            node.put(name, fixed);
        }
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
                throw new IllegalStateException(
                        "no rule for relatedParties" + at + ", a " + value.getNodeType() + "; add one to Anonymiser");
            }
            var text = value.asString();
            var replacement = switch (name) {
                case "name" -> card && at.startsWith(".creditor.") ? MERCHANT : PERSON;
                case "identification" -> account(text, own);
                case "iban" -> ours(text, own) ? OWN_IBAN : OTHER_IBAN;
                default ->
                    throw new IllegalStateException("no rule for relatedParties" + at + "; add one to Anonymiser");
            };
            replaced.add(text);
            node.set(name, StringNode.valueOf(replacement));
        }
    }

    /** The fixed account in the shape MONETA wrote the live one: with or without a bank code, with a {@code 0 }. */
    private static String account(String live, String own) {
        boolean bank = live.contains("/");
        if (ours(live, own)) {
            return bank ? OWN_ACCOUNT : "1337";
        }
        return (live.startsWith("0 ") ? "0 " : "") + (bank ? OTHER_ACCOUNT : "4040404040");
    }

    /** MONETA writes the account it was read from without the id's leading zero. */
    private static boolean ours(String live, String own) {
        return digits(live).contains(digits(own).replaceFirst("^0+", ""));
    }

    /** Wherever peek would hide a value, the fixture holds a fixed one, so no rule missing above lets a person out. */
    private static void onlyFixedWherePeekMasks(JsonNode page) {
        Masking.visit(page, text -> {
            if (!fixed(text)) {
                throw new IllegalStateException("a value peek would mask is not a fixed one ("
                        + Masking.hide(text, true) + "); add a rule to Anonymiser, nothing written");
            }
            return text;
        });
    }

    /** And nothing replaced may still be anywhere in what gets written. */
    private static void leftovers(JsonNode page, List<String> replaced) {
        var written = page.toString();
        for (var text : replaced) {
            if (text.length() > 3 && written.contains(text) && !fixed(text)) {
                throw new IllegalStateException("a replaced value is still in the fixture; nothing written");
            }
        }
    }

    private static boolean fixed(String text) {
        return FIXED.contains(text)
                || text.matches("0000001337:\\d{8}:00001:26110600000000001")
                || text.matches("(VS:(1337)?)?( +KS:(1000)?)?( +SS:(76789)?)?");
    }

    private static String digits(String text) {
        return text.replaceAll("\\D", "");
    }

    private Anonymiser() {}
}
