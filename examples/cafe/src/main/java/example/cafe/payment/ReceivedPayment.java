package example.cafe.payment;

import cz.insuit.moneta.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * What the till needs to match a payment: how much, which variable symbol, and whether it is booked yet.
 *
 * @param message what the payer wrote, once booked
 * @param day the day the payment was made: its value date once booked, the day MONETA took it in while pending
 */
record ReceivedPayment(
        String reference,
        BigDecimal amount,
        String currency,
        @Nullable String variableSymbol,
        @Nullable String message,
        boolean booked,
        @Nullable LocalDate day) {

    static ReceivedPayment of(Transaction t) {
        return new ReceivedPayment(
                t.entryReference(),
                t.amount().amount(),
                t.amount().currency().getCurrencyCode(),
                t.symbols().variable(),
                t.message(),
                t.booked(),
                // The day the payment was made: valueDate once booked (bookingDate can be a later business day),
                // enteredDate while pending.
                t.valueDate() != null ? t.valueDate() : t.enteredDate());
    }
}
