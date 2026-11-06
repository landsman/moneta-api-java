package cz.landsman.moneta;

import java.time.LocalDate;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * One movement on an account. A new component may be added in a minor version, so construct these only in
 * tests and match on them by accessor rather than by record pattern.
 *
 * @param entryReference MONETA's id of the movement
 * @param amount always positive, as MONETA sends it; {@link #signedAmount()} applies the direction
 * @param raw the movement as MONETA sent it, re-serialised as JSON (key order and number formatting may
 *     differ), for whatever this record does not map; left out of {@link #toString()}, since it names the
 *     counterparty
 */
public record Transaction(
        String entryReference,
        Status status,
        Direction direction,
        Money amount,
        @Nullable LocalDate bookingDate,
        @Nullable LocalDate valueDate,
        PaymentSymbols symbols,
        String raw) {

    /** Whether the movement is booked and will not change. */
    public boolean booked() {
        return status == Status.BOOKED;
    }

    /** Positive for money in, negative for money out. */
    public Money signedAmount() {
        return switch (direction) {
            case CREDIT -> amount;
            case DEBIT -> amount.negate();
        };
    }

    @Override
    public String toString() {
        return "Transaction[entryReference=%s, status=%s, direction=%s, amount=%s, bookingDate=%s, valueDate=%s, symbols=%s]"
                .formatted(entryReference, status, direction, amount, bookingDate, valueDate, symbols);
    }

    public enum Direction {
        CREDIT,
        DEBIT
    }

    /** {@code OTHER} for a code this library does not know yet; {@link #raw()} keeps it. */
    public enum Status {
        BOOKED,
        PENDING,
        OTHER
    }

    static Transaction from(MonetaJson.TransactionJson json, String raw) {
        var status = switch (json.status()) {
            case "BOOK" -> Status.BOOKED;
            case "PDNG" -> Status.PENDING;
            default -> Status.OTHER;
        };
        // Unlike the status, a direction is not guessed: a wrong sign on money is worse than a failed read.
        var direction = switch (json.creditDebitIndicator()) {
            case "CRDT" -> Direction.CREDIT;
            case "DBIT" -> Direction.DEBIT;
            default ->
                throw new MonetaException.InvalidResponse(
                        "Transaction " + json.entryReference() + " has an unknown creditDebitIndicator", null);
        };
        var amount = new Money(
                json.amount().value(), Currency.getInstance(json.amount().currency()));
        return new Transaction(
                json.entryReference(),
                status,
                direction,
                amount,
                date(json.bookingDate()),
                date(json.valueDate()),
                PaymentSymbols.parse(json.reference()),
                raw);
    }

    private static @Nullable LocalDate date(MonetaJson.@Nullable DateValue value) {
        return value instanceof MonetaJson.DateValue(var date) ? date : null;
    }
}
