package example.cafe.bill;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A bill as {@code GET /bills} lists it.
 *
 * @param monetaEntryReference MONETA's id of the payment that paid it, as Internet Banka shows it under
 *     "Bankovní reference"; it changes once the payment is booked
 * @param paymentMessage what the payer wrote, once the payment is booked
 * @param paymentBooked whether the payment is booked, so its reference final
 */
record BillView(
        String variableSymbol,
        BigDecimal amount,
        String currency,
        Instant issuedAt,
        boolean paid,
        @Nullable String monetaEntryReference,
        @Nullable String paymentMessage,
        boolean paymentBooked,
        @Nullable Instant paidSeenAt) {

    static BillView of(Bill bill) {
        return new BillView(
                bill.variableSymbol(),
                bill.amount(),
                bill.currency(),
                bill.createdAt(),
                bill.paid(),
                bill.monetaEntryReference(),
                bill.paymentMessage(),
                bill.paymentBooked(),
                bill.paidSeenAt());
    }
}
