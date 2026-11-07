package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * An amount in a currency, exact. Written with at least the currency's minor digits and without trailing zeros
 * beyond them, so {@code 1500} and {@code 1500.00} CZK are equal and hash alike however MONETA formats the
 * number. Never rounded.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        requireNonNull(amount, "amount");
        requireNonNull(currency, "currency");
        var stripped = amount.stripTrailingZeros();
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        amount = stripped.scale() < digits ? stripped.setScale(digits) : stripped;
    }

    /** The same amount with the opposite sign. */
    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    /** {@code 1234.10 CZK}. */
    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
