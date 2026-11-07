package example.cafe.bill;

import static java.util.Objects.requireNonNull;

import cz.landsman.moneta.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

/**
 * A bill the café issued. Its id is its variable symbol: ascending from 1000, never reused, so no two bills
 * can be paid with the same symbol.
 *
 * @param monetaEntryReference MONETA's id of the payment that paid it, its {@code entryReference}; unique, so one
 *     payment pays one bill. MONETA gives a payment a new id once it is booked, so a bill paid by a pending payment
 *     gets the booked one's id when it arrives
 * @param paymentMessage what the payer wrote; MONETA sends it once the payment is booked
 * @param paymentBooked whether {@code monetaEntryReference} is the booked payment's, final, or still the pending
 *     one's
 * @param paidSeenAt when the café first saw that payment. MONETA gives a payment a date and no time, so this,
 *     to the polling interval, is the closest there is to "when it was paid"
 * @param version optimistic locking: two checks running at once (the schedule and a {@code GET /bills/{variableSymbol}}, or
 *     two instances) can each find a different payment for the same bill; the second save fails instead of
 *     overwriting which payment paid it. The unique {@code moneta_entry_reference} would not stop that, the references differ.
 */
record Bill(
        @Id @Nullable Long id,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        @Nullable String monetaEntryReference,
        @Nullable String paymentMessage,
        boolean paymentBooked,
        @Nullable Instant paidSeenAt,
        @Version @Nullable Integer version) {

    /** The column's size; MONETA's own limit on a message is not documented. */
    static final int MESSAGE_LENGTH = 255;

    static Bill issue(BigDecimal amount, Instant now) {
        return new Bill(null, amount, "CZK", now, null, null, false, null, null);
    }

    String variableSymbol() {
        return String.valueOf(requireNonNull(id, "an issued bill has an id"));
    }

    boolean paid() {
        return monetaEntryReference != null;
    }

    /**
     * Paid by this payment. A booked payment replacing the pending one that paid it first keeps the time that one
     * was seen: it is the same money, under the new id MONETA gives it once booked.
     */
    Bill paidWith(Transaction payment, Instant seenAt) {
        var message = payment.message();
        if (message != null && message.length() > MESSAGE_LENGTH) {
            message = message.substring(0, MESSAGE_LENGTH);
        }
        return new Bill(
                id,
                amount,
                currency,
                createdAt,
                payment.entryReference(),
                message,
                payment.booked(),
                paidSeenAt != null ? paidSeenAt : seenAt,
                version);
    }
}
