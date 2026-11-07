package cz.insuit.moneta.internal.json;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.Currency;

public record Amount(BigDecimal value, Currency currency) {
    public Amount {
        requireNonNull(value, "value");
        requireNonNull(currency, "currency");
    }
}
