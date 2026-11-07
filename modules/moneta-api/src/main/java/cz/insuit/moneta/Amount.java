package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.Currency;

record Amount(BigDecimal value, Currency currency) {
    Amount {
        requireNonNull(value, "value");
        requireNonNull(currency, "currency");
    }
}
