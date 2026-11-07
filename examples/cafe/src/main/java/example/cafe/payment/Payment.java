package example.cafe.payment;

import cz.insuit.moneta.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

/**
 * A payment into the café's account, recorded once and kept. One row per payment: when MONETA books a pending one
 * and gives it a new id, the row is updated rather than a second one added.
 *
 * @param monetaEntryReference MONETA's id of the payment: the pending one's until it is booked, the booked one's
 *     after
 * @param variableSymbol as MONETA sent it, leading zeros dropped
 * @param counterpartyAccount the payer's account as MONETA writes it
 * @param counterpartyName the payer's name; MONETA sends it once the payment is booked
 * @param message what the payer wrote; MONETA sends it once the payment is booked
 * @param madeOn the day the payment was made: the entered date while pending, the value date once booked
 * @param firstSeenAt when the café first saw it; MONETA gives a payment no time of day, so this is the closest
 * @param billId the bill it pays, by its variable symbol or by hand; null when none
 */
public record Payment(
        @Id @Nullable Long id,
        String monetaEntryReference,
        boolean booked,
        BigDecimal amount,
        String currency,
        @Nullable String variableSymbol,
        @Nullable String counterpartyAccount,
        @Nullable String counterpartyName,
        @Nullable String message,
        @Nullable LocalDate madeOn,
        Instant firstSeenAt,
        @Nullable Long billId,
        @Version @Nullable Integer version) {

    /** A payment seen for the first time, linked to the bill its symbol names, if there is one. */
    static Payment of(Transaction transaction, Instant seenAt, @Nullable Long billId) {
        return new Payment(
                null,
                transaction.entryReference(),
                transaction.booked(),
                transaction.amount().amount(),
                transaction.amount().currency().getCurrencyCode(),
                PaymentRecording.variableSymbolOf(transaction),
                transaction.counterpartyAccount(),
                transaction.counterpartyName(),
                // Pending, MONETA sends the kind of payment ("Okamžitá platba"), not what the payer wrote.
                transaction.booked() ? transaction.message() : null,
                PaymentRecording.madeOn(transaction),
                seenAt,
                billId,
                null);
    }

    Payment linkedTo(@Nullable Long billId) {
        return new Payment(
                id,
                monetaEntryReference,
                booked,
                amount,
                currency,
                variableSymbol,
                counterpartyAccount,
                counterpartyName,
                message,
                madeOn,
                firstSeenAt,
                billId,
                version);
    }
}
