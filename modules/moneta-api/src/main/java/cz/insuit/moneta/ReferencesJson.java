package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

/**
 * How the movement was made, and a booked card payment's merchant.
 *
 * @param clearingSystemReference {@code PAYMENT_ORDER_DOMESTIC} for a transfer, {@code PAYMENT_CARD} for a card
 *     payment, each prefixed {@code BLOCKED_} while pending (live, 2026-11-06 to 2026-11-10)
 * @param transactionDescription a booked card payment's merchant, which the pending one sends as the creditor
 */
record ReferencesJson(
        @Nullable String clearingSystemReference, @Nullable String transactionDescription) {}
