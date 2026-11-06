package cz.landsman.moneta;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One movement on an account.
 *
 * @param entryReference MONETA's id of the movement
 * @param status {@code BOOK} once booked; anything else may still change
 * @param amount always positive, as MONETA sends it; {@link #signedAmount()} applies the direction
 * @param raw the movement exactly as MONETA sent it, as JSON, for whatever this record does not map
 */
public record Transaction(
        String entryReference,
        String status,
        Direction direction,
        BigDecimal amount,
        String currency,
        @Nullable LocalDate bookingDate,
        @Nullable LocalDate valueDate,
        PaymentSymbols symbols,
        String raw) {

    /** Whether the movement is booked and will not change. */
    public boolean booked() {
        return "BOOK".equals(status);
    }

    /** Positive for money in, negative for money out. */
    public BigDecimal signedAmount() {
        return direction == Direction.DEBIT ? amount.negate() : amount;
    }

    public enum Direction {
        CREDIT,
        DEBIT
    }

    static Transaction from(MonetaJson.TransactionJson json, String raw) {
        var direction = switch (json.creditDebitIndicator()) {
            case "CRDT" -> Direction.CREDIT;
            case "DBIT" -> Direction.DEBIT;
            default ->
                throw new IllegalArgumentException("Unknown creditDebitIndicator: " + json.creditDebitIndicator());
        };
        return new Transaction(
                json.entryReference(),
                json.status(),
                direction,
                json.amount().value(),
                json.amount().currency(),
                date(json.bookingDate()),
                date(json.valueDate()),
                PaymentSymbols.parse(json.reference()),
                raw);
    }

    private static @Nullable LocalDate date(MonetaJson.@Nullable DateValue value) {
        return value == null ? null : value.date();
    }
}
