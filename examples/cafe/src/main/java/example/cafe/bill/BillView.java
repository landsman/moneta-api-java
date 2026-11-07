package example.cafe.bill;

import example.cafe.payment.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A bill with the payments linked to it, as {@code GET /bills} lists it.
 *
 * @param paid the sum of the linked payments, pending ones included
 * @param paidSeenAt when the café first saw the payment that covered the bill; null until it is covered
 */
record BillView(
        String variableSymbol,
        BigDecimal amount,
        String currency,
        Instant issuedAt,
        BigDecimal paid,
        BillStatus status,
        @Nullable Instant paidSeenAt,
        List<Payment> payments) {

    static BillView of(Bill bill, List<Payment> payments) {
        var paid = payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var status = BillStatus.of(bill.amount(), paid);
        var covered = status == BillStatus.PAID || status == BillStatus.OVERPAID;
        var paidSeenAt = covered
                ? payments.stream()
                        .map(Payment::firstSeenAt)
                        .max(Comparator.naturalOrder())
                        .orElse(null)
                : null;
        return new BillView(
                bill.variableSymbol(),
                bill.amount(),
                bill.currency(),
                bill.createdAt(),
                paid,
                status,
                paidSeenAt,
                payments);
    }
}
