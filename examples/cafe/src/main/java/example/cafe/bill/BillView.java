package example.cafe.bill;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A bill as {@code GET /bills} lists it.
 *
 * @param monetaEntryReference MONETA's id of the payment that paid it, as Internet Banka shows it under
 *     "Bankovní reference"
 */
record BillView(
        String variableSymbol,
        BigDecimal amount,
        String currency,
        Instant issuedAt,
        boolean paid,
        @Nullable String monetaEntryReference,
        @Nullable Instant paidSeenAt) {

    static BillView of(Bill bill) {
        return new BillView(
                bill.variableSymbol(),
                bill.amount(),
                bill.currency(),
                bill.createdAt(),
                bill.paid(),
                bill.monetaEntryReference(),
                bill.paidSeenAt());
    }
}
