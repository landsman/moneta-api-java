package cz.landsman.moneta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * MONETA's JSON, one record per object, named exactly as MONETA names the keys. Jackson binds them by those
 * names, so a key is written once, here, and the rest of the library reads typed fields. Unknown keys are
 * ignored; a key this file does not name is still in {@link Transaction#raw()}.
 */
final class MonetaJson {

    record AccountsPage(@Nullable List<AccountJson> accounts) {
        List<AccountJson> all() {
            return accounts == null ? List.of() : accounts;
        }
    }

    record AccountJson(
            String id,
            String currency,
            @Nullable String nameI18N,
            @Nullable Identification identification) {}

    record Identification(@Nullable String iban) {}

    /** The transactions stay JSON trees here, so each one can be kept verbatim besides being bound. */
    record TransactionsPage(@Nullable List<JsonNode> transactions, int pageCount) {
        List<JsonNode> all() {
            return transactions == null ? List.of() : transactions;
        }
    }

    record TransactionJson(
            String entryReference,
            String status,
            String creditDebitIndicator,
            Amount amount,
            @Nullable DateValue bookingDate,
            @Nullable DateValue valueDate,
            @Nullable EntryDetails entryDetails) {

        @Nullable
        String reference() {
            if (entryDetails == null) {
                return null;
            }
            var details = entryDetails.transactionDetails();
            var remittance = details == null ? null : details.remittanceInformation();
            var structured = remittance == null ? null : remittance.structured();
            var creditor = structured == null ? null : structured.creditorReferenceInformation();
            return creditor == null ? null : creditor.reference();
        }
    }

    record Amount(BigDecimal value, String currency) {}

    record DateValue(LocalDate date) {}

    record EntryDetails(@Nullable TransactionDetails transactionDetails) {}

    record TransactionDetails(@Nullable RemittanceInformation remittanceInformation) {}

    record RemittanceInformation(@Nullable Structured structured) {}

    record Structured(@Nullable CreditorReferenceInformation creditorReferenceInformation) {}

    record CreditorReferenceInformation(@Nullable String reference) {}

    private MonetaJson() {}
}
