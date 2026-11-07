package peek;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What peek prints to a terminal, and so to a scrollback or a pasted log: nobody's name, account or message. */
class MaskingTest {

    // language=JSON
    private static final String MOVEMENT = """
            {"entryReference": "5555555555:20261109:00042:26110600000000077",
             "amount": {"value": 30.0, "currency": "CZK"},
             "entryDetails": {"transactionDetails": {
               "relatedParties": {"debtor": {"name": "Svoboda, Petr", "postalAddress": {"townName": "Brno"}},
                                  "debtorAccount": {"identification": {"other": {"identification": "7777777777/0300"}}}},
               "remittanceInformation": {"unstructured": "za obed u Petra"}}}}""";

    @Test
    void masksEverythingUnderThePartiesTheMessageAndTheReference() {
        var printed = Masking.mask(Peek.JSON.readTree(MOVEMENT), false).toString();

        for (var live : new String[] {"Svoboda", "Brno", "7777777777", "5555555555", "za obed"}) {
            assertFalse(printed.contains(live), live + " is still in " + printed);
        }
        assertTrue(printed.contains("\"value\":30.0"), printed);
        assertTrue(printed.contains("<13 chars>"), printed);
    }

    @Test
    void masksAStringInAnArrayAndThePaymentSymbols() {
        var movement = MOVEMENT.replace("{\"townName\": \"Brno\"}", "{\"addressLine\": [\"Dlouha 1\", \"Brno\"]}")
                .replace(
                        "\"remittanceInformation\": {",
                        "\"remittanceInformation\": {\"structured\": {\"creditorReferenceInformation\":"
                                + " {\"reference\": \"VS:8001011234\"}}, ");

        var printed = Masking.mask(Peek.JSON.readTree(movement), false).toString();

        for (var live : new String[] {"Dlouha", "Brno", "8001011234"}) {
            assertFalse(printed.contains(live), live + " is still in " + printed);
        }
    }

    @Test
    void shapeKeepsTheLayoutAndNotTheText() {
        assertEquals("xxxxx  *xxxx 99", Masking.hide("SUMUP  *Café 42", true));
    }

    @Test
    void leavesMonetasPendingPlaceholderReadable() {
        var pending = MOVEMENT.replace("za obed u Petra", Masking.INSTANT_PLACEHOLDER);
        assertTrue(Masking.mask(Peek.JSON.readTree(pending), false).toString().contains(Masking.INSTANT_PLACEHOLDER));
    }
}
