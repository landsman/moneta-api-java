package cz.insuit.moneta;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One movement on an account. A new component may be added in a minor version, so construct these only in
 * tests and match on them by accessor rather than by record pattern.
 *
 * @param entryReference MONETA's id of the movement
 * @param amount signed: positive for money in, negative for money out, so amounts add up as they are
 * @param enteredDate the day the bank took the movement in; the only date a pending one has
 * @param bookingDate null while the movement is pending
 * @param valueDate null while the movement is pending
 * @param raw the movement as MONETA sent it, re-serialised as JSON (key order and number formatting may
 *     differ), for whatever this record does not map; left out of {@link #toString()}, since it names the
 *     counterparty
 */
public record Transaction(
        String entryReference,
        Status status,
        Direction direction,
        Money amount,
        @Nullable LocalDate enteredDate,
        @Nullable LocalDate bookingDate,
        @Nullable LocalDate valueDate,
        PaymentSymbols symbols,
        String raw) {

    /** Whether the movement is booked and will not change. */
    public boolean booked() {
        return status == Status.BOOKED;
    }

    @Override
    public String toString() {
        return "Transaction[entryReference=%s, status=%s, direction=%s, amount=%s, enteredDate=%s, bookingDate=%s, valueDate=%s, symbols=%s]"
                .formatted(entryReference, status, direction, amount, enteredDate, bookingDate, valueDate, symbols);
    }

    /** Which way the money moved; {@link #amount()} already carries it as the sign. */
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
                        "unknown creditDebitIndicator '" + json.creditDebitIndicator() + "'", null);
        };
        // MONETA sends the amount unsigned beside the direction. A negative one would be signed twice, so it is
        // refused for the same reason as an unknown direction.
        if (json.amount().value().signum() < 0) {
            throw new MonetaException.InvalidResponse("a negative amount beside creditDebitIndicator", null);
        }
        var unsigned = new Money(json.amount().value(), json.amount().currency());
        var amount = switch (direction) {
            case CREDIT -> unsigned;
            case DEBIT -> unsigned.negate();
        };
        return new Transaction(
                json.entryReference(),
                status,
                direction,
                amount,
                date(json.enteredDate()),
                date(json.bookingDate()),
                date(json.valueDate()),
                PaymentSymbols.parse(json.reference()),
                raw);
    }

    private static @Nullable LocalDate date(MonetaJson.@Nullable DateValue value) {
        return value instanceof MonetaJson.DateValue(var date) ? date : null;
    }
}
