package example.cafe.bill;

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
 * @param monetaEntryReference MONETA's id of the payment that paid it, its {@code entryReference}; unique, so one
 *     payment pays one bill
 * @param paidSeenAt when the café first saw that payment. MONETA gives a payment a date and no time, so this,
 *     to the polling interval, is the closest there is to "when it was paid"
 * @param version optimistic locking: two checks running at once (the schedule and {@code POST /bills/check}, or
 *     two instances) can each find a different payment for the same bill; the second save fails instead of
 *     overwriting which payment paid it. The unique {@code moneta_entry_reference} would not stop that, the references differ.
 */
record Bill(
        @Id @Nullable Long id,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        @Nullable String monetaEntryReference,
        @Nullable Instant paidSeenAt,
        @Version @Nullable Integer version) {

    static Bill issue(BigDecimal amount, Instant now) {
        return new Bill(null, amount, "CZK", now, null, null, null);
    }

    String variableSymbol() {
        return String.valueOf(requireNonNull(id, "an issued bill has an id"));
    }

    boolean paid() {
        return monetaEntryReference != null;
    }

    Bill paidWith(String monetaEntryReference, Instant seenAt) {
        return new Bill(id, amount, currency, createdAt, monetaEntryReference, seenAt, version);
    }
}
