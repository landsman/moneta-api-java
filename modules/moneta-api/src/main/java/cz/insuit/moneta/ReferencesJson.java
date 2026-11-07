package cz.insuit.moneta;

import org.jspecify.annotations.Nullable;

/**
 * {@code clearingSystemReference} names how the movement was made: {@code PAYMENT_ORDER_DOMESTIC} for a transfer,
 * {@code BLOCKED_PAYMENT_ORDER_DOMESTIC} while it is pending, {@code BLOCKED_PAYMENT_CARD} for a pending card
 * payment (live, 2026-11-07).
 */
record ReferencesJson(@Nullable String clearingSystemReference) {}
