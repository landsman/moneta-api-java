package cz.insuit.moneta;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * One movement on an account. A new component may be added in a minor version, so construct these only in
 * tests and match on them by accessor rather than by record pattern.
 *
 * @param amount signed: positive for money in, negative for money out, so amounts add up as they are
 * @param entryReference MONETA's id of the movement. <b>It changes when a pending movement is booked</b>: 19 digits
 *     while pending, {@code account:bookingDate:sequence:reference} once booked (live, 2026-11-07). Deduplicate by
 *     it and a payment counts twice.
 * @param enteredDate the day the bank took the movement in; the only date a pending one has, null on a booked
 *     credit
 * @param bookingDate null while the movement is pending; can be a later business day than the payment
 * @param valueDate null while the movement is pending; once booked, the payment's day, and the one the date range
 *     filters it by
 * @param message the payer's message, sent once booked; while an instant payment is pending, "Okamžitá platba"
 * @param counterpartyName the payer of a credit, the payee of a debit; sent once booked, for credits
 * @param counterpartyAccount the other side's account as MONETA writes it, {@code number/bank}
 * @param reversed whether MONETA marks the movement as a reversal
 * @param method how the money moved: a transfer, a card payment, or {@code OTHER} for anything not mapped yet. A
 *     card payment is a debit whose counterparty name is the merchant and which has no counterparty account; the
 *     merchant text differs between the pending hold and the booked payment (live, 2026-11-07 and 2026-11-10)
 * @param raw the movement as MONETA sent it, re-serialised as JSON (key order and number formatting may
 *     differ), for whatever this record does not map. It, the message and the counterparty are left out of
 *     {@link #toString()}, so logging a transaction does not log who paid or what they wrote
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
        @Nullable String message,
        @Nullable String counterpartyName,
        @Nullable String counterpartyAccount,
        boolean reversed,
        Method method,
        String raw) {

    /** Whether the movement is booked and will not change. */
    public boolean booked() {
        return status == Status.BOOKED;
    }

    @Override
    public String toString() {
        return "Transaction[entryReference=%s, status=%s, direction=%s, amount=%s, enteredDate=%s, bookingDate=%s, valueDate=%s, symbols=%s, reversed=%s, method=%s]"
                .formatted(
                        entryReference,
                        status,
                        direction,
                        amount,
                        enteredDate,
                        bookingDate,
                        valueDate,
                        symbols,
                        reversed,
                        method);
    }

    /** Which way the money moved; {@link #amount()} already carries it as the sign. */
    public enum Direction {
        CREDIT,
        DEBIT
    }

    /**
     * How the money moved, read from MONETA's {@code clearingSystemReference}. {@code OTHER} for any other value,
     * which {@link #raw()} keeps.
     */
    // TODO: fees, interest, ATM withdrawals and the rest, once one is seen live:
    //  https://github.com/landsman/moneta-api-java/issues/66
    public enum Method {
        /** A domestic payment order, {@code PAYMENT_ORDER_DOMESTIC}; an instant one included. */
        TRANSFER,
        /** A card payment, {@code PAYMENT_CARD}; while pending, a hold that may lapse. */
        CARD,
        OTHER
    }

    /** {@code OTHER} for a code this library does not know yet; {@link #raw()} keeps it. */
    public enum Status {
        BOOKED,
        PENDING,
        OTHER
    }

    static Transaction from(TransactionJson json, String raw) {
        var status = switch (json.status()) {
            case "BOOK" -> Status.BOOKED;
            case "PDNG" -> Status.PENDING;
            default -> Status.OTHER;
        };
        // Unlike the status, a direction is not guessed.
        var indicator = CreditDebit.of(json.creditDebitIndicator());
        var direction = indicator.direction;
        var amount = indicator.signed(json.amount());
        return new Transaction(
                json.entryReference(),
                status,
                direction,
                amount,
                date(json.enteredDate()),
                date(json.bookingDate()),
                date(json.valueDate()),
                PaymentSymbols.parse(json.reference()),
                json.message(),
                json.counterpartyName(direction == Direction.CREDIT),
                json.counterpartyAccount(direction == Direction.CREDIT),
                Boolean.TRUE.equals(json.reversalIndicator()),
                json.method(),
                raw);
    }

    private static @Nullable LocalDate date(@Nullable DateValueJson value) {
        return value instanceof DateValueJson(var date) ? date : null;
    }
}
