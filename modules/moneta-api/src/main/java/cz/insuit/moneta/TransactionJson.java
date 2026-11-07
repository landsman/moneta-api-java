package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
