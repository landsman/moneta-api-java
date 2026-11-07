package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.util.Currency;

record AmountJson(BigDecimal value, Currency currency) {
    AmountJson {
        requireNonNull(value, "value");
        requireNonNull(currency, "currency");
    }
}
