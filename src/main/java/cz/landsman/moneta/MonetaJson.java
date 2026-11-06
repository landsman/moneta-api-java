package cz.landsman.moneta;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * MONETA's JSON, one record per object, named exactly as MONETA names the keys. Jackson binds them by those
 * names, so a key is written once, here, and the rest of the library reads typed fields. Unknown keys are
 * ignored. A key the library relies on is checked in the compact constructor, so a missing one fails at
 * binding, as {@link MonetaException.InvalidResponse}, and not as a null three calls later.
 */
final class MonetaJson {

    /** A page of a list. The items stay JSON trees, so each one can be kept verbatim besides being bound. */
    sealed interface Page permits AccountsPage, TransactionsPage {
        List<JsonNode> items();

        /** The page MONETA says it returned; null when it did not say. */
        @Nullable
        Integer pageNumber();

        /** Null when MONETA did not say. */
        @Nullable
        Integer pageCount();
    }

    record AccountsPage(
            List<JsonNode> accounts,
            @Nullable Integer pageNumber,
            @Nullable Integer pageCount) implements Page {
        AccountsPage {
            accounts = requireNonNullElse(accounts, List.of());
        }

        @Override
        public List<JsonNode> items() {
            return accounts;
        }
    }

    record TransactionsPage(
            List<JsonNode> transactions,
            @Nullable Integer pageNumber,
            @Nullable Integer pageCount) implements Page {
        TransactionsPage {
            transactions = requireNonNullElse(transactions, List.of());
        }

        @Override
        public List<JsonNode> items() {
            return transactions;
        }
    }

    record AccountJson(
            String id,
            String currency,
            @Nullable String nameI18N,
            @Nullable Identification identification) {
        AccountJson {
            requireNonNull(id, "id");
            requireNonNull(currency, "currency");
        }
    }

    record Identification(@Nullable String iban) {}

    record TransactionJson(
            String entryReference,
            String status,
            String creditDebitIndicator,
            Amount amount,
            @Nullable DateValue bookingDate,
            @Nullable DateValue valueDate,
            @Nullable EntryDetails entryDetails) {
        TransactionJson {
            requireNonNull(entryReference, "entryReference");
            requireNonNull(status, "status");
            requireNonNull(creditDebitIndicator, "creditDebitIndicator");
            requireNonNull(amount, "amount");
        }

        /** The string the payment symbols are in. A nested record pattern matches only if no level is null. */
        @Nullable
        String reference() {
            return entryDetails
                            instanceof
                            EntryDetails(
                                    TransactionDetails(
                                            RemittanceInformation(
                                                    Structured(CreditorReferenceInformation(var reference)))))
                    ? reference
                    : null;
        }
    }

    record Amount(BigDecimal value, String currency) {
        Amount {
            requireNonNull(value, "value");
            requireNonNull(currency, "currency");
        }
    }

    record DateValue(LocalDate date) {
        DateValue {
            requireNonNull(date, "date");
        }
    }

    record EntryDetails(@Nullable TransactionDetails transactionDetails) {}

    record TransactionDetails(@Nullable RemittanceInformation remittanceInformation) {}

    record RemittanceInformation(@Nullable Structured structured) {}

    record Structured(@Nullable CreditorReferenceInformation creditorReferenceInformation) {}

    record CreditorReferenceInformation(@Nullable String reference) {}

    private MonetaJson() {}
}
