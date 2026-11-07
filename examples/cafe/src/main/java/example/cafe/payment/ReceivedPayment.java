package example.cafe.payment;

import cz.insuit.moneta.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * What the till needs to match a payment: how much, which variable symbol, and whether it is booked yet.
 *
 * @param day the booking day, or the day MONETA took it in while it is pending
 */
record ReceivedPayment(
        String reference,
        BigDecimal amount,
        String currency,
        @Nullable String variableSymbol,
        boolean booked,
        @Nullable LocalDate day) {

    static ReceivedPayment of(Transaction t) {
        return new ReceivedPayment(
                t.entryReference(),
                t.amount().amount(),
                t.amount().currency().getCurrencyCode(),
                t.symbols().variable(),
                t.booked(),
                t.bookingDate() != null ? t.bookingDate() : t.enteredDate());
    }
}
