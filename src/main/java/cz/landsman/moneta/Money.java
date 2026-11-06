package cz.landsman.moneta;

import java.math.BigDecimal;
import java.util.Currency;

/** An amount in a currency, exact. */
public record Money(BigDecimal amount, Currency currency) {

    public Money negate() {
        return new Money(amount.negate(), currency);
    }
}
