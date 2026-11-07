package peek;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** What a fixture must never carry into the public repository: who paid, from which account, what they wrote. */
class AnonymiserTest {

    // As MONETA writes it: the id with a leading zero, the account in a movement without.
    private static final String OWN = "0555555555";

    // language=JSON
    private static final String BOOKED_CREDIT = """
            {"entryReference": "5555555555:20261109:00042:26110600000000077", "status": "BOOK",
             "entryDetails": {"transactionDetails": {
               "references": {"clearingSystemReference": "PAYMENT_ORDER_DOMESTIC"},
               "relatedParties": {"debtor": {"name": "Svoboda, Petr"},
                                  "debtorAccount": {"identification": {"other": {"identification": "0 7777777777/0300"}}},
                                  "creditorAccount": {"identification": {"other": {"identification": "5555555555"}}}},
               "remittanceInformation": {"unstructured": "za obed u Petra"}}}}""";

    // language=JSON
    private static final String PENDING_CARD = """
            {"entryReference": "2611070000000000099", "status": "PDNG",
             "entryDetails": {"transactionDetails": {
               "references": {"clearingSystemReference": "BLOCKED_PAYMENT_CARD"},
               "relatedParties": {"creditor": {"name": "SHOP  *REAL NAME OF A MERCHANT"},
                                  "debtorAccount": {"identification": {"other": {"identification": "555555555/0600"}}}}}}}""";

    @Test
    void replacesThePayerTheirAccountTheMessageAndTheReference() {
        var written = fixture(BOOKED_CREDIT).toString();

        for (var live : new String[] {"Svoboda", "Petr", "7777777777", "5555555555:", "00042", "za obed"}) {
            assertFalse(written.contains(live), live + " is still in " + written);
        }
        assertTrue(written.contains("\"Novak, Jan\""), written);
        assertTrue(written.contains(Anonymiser.OTHER_ACCOUNT), written);
        // The own account came without a bank code, as MONETA writes it on a credit, and keeps that shape.
        assertTrue(written.contains("\"identification\":\"1337\""), written);
        assertTrue(written.contains("0000001337:20261109:00001:26110600000000001"), written);
        assertTrue(written.contains(Anonymiser.MESSAGE), written);
    }

    @Test
    void givesACardPaymentTheFixedMerchantInTheDescriptorsWidths() {
        var movement = fixture(PENDING_CARD).get("transactions").get(0);
        var creditor = movement.at("/entryDetails/transactionDetails/relatedParties/creditor/name")
                .asString();

        assertEquals(Anonymiser.MERCHANT, creditor);
        assertEquals(40, creditor.length());
        assertEquals("2600000000000000001", movement.get("entryReference").asString());
        assertEquals(
                Anonymiser.OWN_ACCOUNT,
                movement.at(
                                "/entryDetails/transactionDetails/relatedParties/debtorAccount/identification/other/identification")
                        .asString());
    }

    @Test
    void keepsMonetasOwnPlaceholderForAPendingMessage() {
        var pending = BOOKED_CREDIT.replace("za obed u Petra", Masking.INSTANT_PLACEHOLDER);
        assertTrue(fixture(pending).toString().contains(Masking.INSTANT_PLACEHOLDER));
    }

    @Test
    void refusesAPartyValueItHasNoRuleFor() {
        var withAddress = BOOKED_CREDIT.replace(
                "\"debtor\": {\"name\": \"Svoboda, Petr\"}",
                "\"debtor\": {\"name\": \"Svoboda, Petr\", \"postalAddress\": {\"townName\": \"Brno\"}}");
        assertThrows(IllegalStateException.class, () -> fixture(withAddress));
    }

    @Test
    void replacesTheSymbolsAndTheIdsThatRepeatThemKeepingTheirLayout() {
        var withSymbols = BOOKED_CREDIT
                .replace(
                        "\"references\": {\"clearingSystemReference\": \"PAYMENT_ORDER_DOMESTIC\"}",
                        "\"references\": {\"clearingSystemReference\": \"PAYMENT_ORDER_DOMESTIC\","
                                + " \"endToEndIdentification\": \"8001011234\","
                                + " \"paymentInformationIdentification\": \"777123456\"}")
                .replace(
                        "\"remittanceInformation\": {",
                        "\"remittanceInformation\": {\"structured\": {\"creditorReferenceInformation\":"
                                + " {\"reference\": \"VS:8001011234  SS:777123456\"}}, ");

        var written = fixture(withSymbols).toString();

        assertFalse(written.contains("8001011234"), written);
        assertFalse(written.contains("777123456"), written);
        assertTrue(written.contains("\"VS:1337  SS:76789\""), written);
        assertTrue(written.contains("\"endToEndIdentification\":\"1337\""), written);
    }

    @Test
    void refusesAnArrayUnderTheParties() {
        var withAddress = BOOKED_CREDIT.replace(
                "\"debtor\": {\"name\": \"Svoboda, Petr\"}",
                "\"debtor\": {\"name\": \"Svoboda, Petr\", \"postalAddress\": {\"addressLine\": [\"Dlouha 1\"]}}");
        assertThrows(IllegalStateException.class, () -> fixture(withAddress));
    }

    @Test
    void keepsTheOwnAccountWithoutABankCodeAsMonetaWroteIt() {
        var bare = PENDING_CARD.replace("555555555/0600", "555555555");
        var movement = fixture(bare).get("transactions").get(0);
        assertEquals(
                "1337",
                movement.at(
                                "/entryDetails/transactionDetails/relatedParties/debtorAccount/identification/other/identification")
                        .asString());
    }

    @Test
    void stopsAValueOutsideThePartiesThatPeekWouldMask() {
        var withHolder =
                BOOKED_CREDIT.replace("\"references\": {", "\"cardholderName\": \"Petr Svoboda\", \"references\": {");
        assertThrows(IllegalStateException.class, () -> fixture(withHolder));
    }

    private static ObjectNode fixture(String movement) {
        return Anonymiser.fixture((ObjectNode) Peek.JSON.readTree(movement), OWN);
    }
}
