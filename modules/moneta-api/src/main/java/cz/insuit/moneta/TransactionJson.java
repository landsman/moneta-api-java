package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

record TransactionJson(
        String entryReference,
        String status,
        String creditDebitIndicator,
        AmountJson amount,
        @Nullable DateValueJson enteredDate,
        @Nullable DateValueJson bookingDate,
        @Nullable DateValueJson valueDate,
        @Nullable Boolean reversalIndicator,
        @Nullable EntryDetailsJson entryDetails) {
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
                .map(RemittanceInformationJson::structured)
                .map(StructuredJson::creditorReferenceInformation)
                .map(CreditorReferenceInformationJson::reference)
                .orElse(null);
    }

    /** The payer's message; "Okamžitá platba" while an instant payment is pending (live, 2026-11-06). */
    @Nullable
    String message() {
        return remittance().map(RemittanceInformationJson::unstructured).orElse(null);
    }

    /** The other side's name: the debtor of a credit, the creditor of a debit. Sent once booked. */
    @Nullable
    String counterpartyName(boolean credit) {
        return parties()
                .map(parties -> credit ? parties.debtor() : parties.creditor())
                .map(PartyJson::name)
                .orElse(null);
    }

    /** The other side's account as MONETA writes it, {@code number/bank}, sometimes with a space after a prefix. */
    @Nullable
    String counterpartyAccount(boolean credit) {
        return parties()
                .map(parties -> credit ? parties.debtorAccount() : parties.creditorAccount())
                .map(PartyAccountJson::identification)
                .map(AccountIdentificationJson::other)
                .map(OtherIdentificationJson::identification)
                .orElse(null);
    }

    private Optional<TransactionDetailsJson> details() {
        return Optional.ofNullable(entryDetails).map(EntryDetailsJson::transactionDetails);
    }

    private Optional<RemittanceInformationJson> remittance() {
        return details().map(TransactionDetailsJson::remittanceInformation);
    }

    private Optional<RelatedPartiesJson> parties() {
        return details().map(TransactionDetailsJson::relatedParties);
    }
}
