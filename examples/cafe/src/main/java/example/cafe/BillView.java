package example.cafe;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** A bill as {@code GET /bills} lists it. */
record BillView(
        String variableSymbol,
        BigDecimal amount,
        String currency,
        Instant issuedAt,
        boolean paid,
        @Nullable String paidBy,
        @Nullable Instant paidSeenAt) {

    static BillView of(Bill bill) {
        return new BillView(
                bill.variableSymbol(),
                bill.amount(),
                bill.currency(),
                bill.createdAt(),
                bill.paid(),
                bill.paidBy(),
                bill.paidSeenAt());
    }
}
