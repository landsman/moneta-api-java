package cz.insuit.moneta.internal.json;

import cz.insuit.moneta.MonetaException;
import cz.insuit.moneta.Money;
import cz.insuit.moneta.Transaction;

/**
 * {@code creditDebitIndicator}, which way the money moved. MONETA sends an amount unsigned beside it, on a
 * movement and on a balance alike. Kept a string in the records, so a refused value can be named.
 */
public enum CreditDebit {
    CRDT(Transaction.Direction.CREDIT),
    DBIT(Transaction.Direction.DEBIT);

    public final Transaction.Direction direction;

    CreditDebit(Transaction.Direction direction) {
        this.direction = direction;
    }

    /** An unknown value is refused, not guessed: a wrong sign on money is worse than a failed read. */
    public static CreditDebit of(String code) {
        return switch (code) {
            case "CRDT" -> CRDT;
            case "DBIT" -> DBIT;
            default -> throw new MonetaException.InvalidResponse("unknown creditDebitIndicator '" + code + "'", null);
        };
    }

    /** The amount with this direction as its sign. A negative one would be signed twice, so it is refused. */
    public Money signed(Amount amount) {
        if (amount.value().signum() < 0) {
            throw new MonetaException.InvalidResponse("a negative amount beside creditDebitIndicator", null);
        }
        var unsigned = new Money(amount.value(), amount.currency());
        return switch (this) {
            case CRDT -> unsigned;
            case DBIT -> unsigned.negate();
        };
    }
}
