package example.cafe;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

/**
 * A bill the café issued. Its id is its variable symbol: ascending from 1000, never reused, so no two bills
 * can be paid with the same symbol.
 *
 * @param paidBy the {@code entryReference} of the payment that paid it; unique, so one payment pays one bill
 * @param paidSeenAt when the café first saw that payment. MONETA gives a payment a date and no time, so this,
 *     to the polling interval, is the closest there is to "when it was paid"
 */
record Bill(
        @Id @Nullable Long id,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        @Nullable String paidBy,
        @Nullable Instant paidSeenAt,
        @Version @Nullable Integer version) {

    static Bill issue(BigDecimal amount, Instant now) {
        return new Bill(null, amount, "CZK", now, null, null, null);
    }

    String variableSymbol() {
        return String.valueOf(requireNonNull(id, "an issued bill has an id"));
    }

    boolean paid() {
        return paidBy != null;
    }

    Bill paidBy(String entryReference, Instant seenAt) {
        return new Bill(id, amount, currency, createdAt, entryReference, seenAt, version);
    }
}
