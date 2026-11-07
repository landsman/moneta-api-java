package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
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
            // A 200 without the key is not an empty list: a maintenance page or a renamed key would read as no data.
            requireNonNull(accounts, "accounts");
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
            requireNonNull(transactions, "transactions");
        }

        @Override
        public List<JsonNode> items() {
            return transactions;
        }
    }

    record AccountJson(
            String id,
            Currency currency,
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
            @Nullable DateValue enteredDate,
            @Nullable DateValue bookingDate,
            @Nullable DateValue valueDate,
            @Nullable Boolean reversalIndicator,
            @Nullable EntryDetails entryDetails) {
        TransactionJson {
            requireNonNull(entryReference, "entryReference");
            requireNonNull(status, "status");
            requireNonNull(creditDebitIndicator, "creditDebitIndicator");
            requireNonNull(amount, "amount");
        }

        /** The string the payment symbols are in. */
        @Nullable
        String reference() {
            return remittance()
                    .map(RemittanceInformation::structured)
                    .map(Structured::creditorReferenceInformation)
                    .map(CreditorReferenceInformation::reference)
                    .orElse(null);
        }

        /** The payer's message; "Okamžitá platba" while an instant payment is pending (live, 2026-11-06). */
        @Nullable
        String message() {
            return remittance().map(RemittanceInformation::unstructured).orElse(null);
        }

        /** The other side's name: the debtor of a credit, the creditor of a debit. Sent once booked. */
        @Nullable
        String counterpartyName(boolean credit) {
            return parties()
                    .map(parties -> credit ? parties.debtor() : parties.creditor())
                    .map(Party::name)
                    .orElse(null);
        }

        /** The other side's account as MONETA writes it, {@code number/bank}, sometimes with a space after a prefix. */
        @Nullable
        String counterpartyAccount(boolean credit) {
            return parties()
                    .map(parties -> credit ? parties.debtorAccount() : parties.creditorAccount())
                    .map(PartyAccount::identification)
                    .map(AccountIdentification::other)
                    .map(OtherIdentification::identification)
                    .orElse(null);
        }

        private Optional<TransactionDetails> details() {
            return Optional.ofNullable(entryDetails).map(EntryDetails::transactionDetails);
        }

        private Optional<RemittanceInformation> remittance() {
            return details().map(TransactionDetails::remittanceInformation);
        }

        private Optional<RelatedParties> parties() {
            return details().map(TransactionDetails::relatedParties);
        }
    }

    record Amount(BigDecimal value, Currency currency) {
        Amount {
            requireNonNull(value, "value");
            requireNonNull(currency, "currency");
        }
    }

    /** {@code GET …/accounts/{id}/balance}: one entry per type, {@code CLBD} and {@code CLAV} (live, 2026-11-07). */
    record BalancesJson(List<BalanceJson> balances) {
        BalancesJson {
            requireNonNull(balances, "balances");
        }
    }

    record BalanceJson(Amount amount, String creditDebitIndicator, DateTimeValue date, BalanceType type) {
        BalanceJson {
            requireNonNull(amount, "amount");
            requireNonNull(creditDebitIndicator, "creditDebitIndicator");
            requireNonNull(date, "date");
            requireNonNull(type, "type");
        }
    }

    record DateTimeValue(Instant dateTime) {
        DateTimeValue {
            requireNonNull(dateTime, "dateTime");
        }
    }

    record BalanceType(CodeOrProprietary codeOrProprietary) {
        BalanceType {
            requireNonNull(codeOrProprietary, "codeOrProprietary");
        }
    }

    record CodeOrProprietary(@Nullable String code) {}

    /** A pending movement sends {@code {"date": null}} for its booking and value dates (live, 2026-11-06). */
    record DateValue(@Nullable LocalDate date) {}

    record EntryDetails(@Nullable TransactionDetails transactionDetails) {}

    record TransactionDetails(
            @Nullable RemittanceInformation remittanceInformation,
            @Nullable RelatedParties relatedParties) {}

    record RemittanceInformation(
            @Nullable Structured structured, @Nullable String unstructured) {}

    record Structured(@Nullable CreditorReferenceInformation creditorReferenceInformation) {}

    record CreditorReferenceInformation(@Nullable String reference) {}

    record RelatedParties(
            @Nullable Party debtor,
            @Nullable Party creditor,
            @Nullable PartyAccount debtorAccount,
            @Nullable PartyAccount creditorAccount) {}

    record Party(@Nullable String name) {}

    record PartyAccount(@Nullable AccountIdentification identification) {}

    record AccountIdentification(@Nullable OtherIdentification other) {}

    record OtherIdentification(@Nullable String identification) {}

    private MonetaJson() {}
}
