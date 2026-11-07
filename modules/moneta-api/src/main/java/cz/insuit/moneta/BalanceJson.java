package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

record BalanceJson(Amount amount, String creditDebitIndicator, DateTimeValue date, BalanceType type) {
    BalanceJson {
        requireNonNull(amount, "amount");
        requireNonNull(creditDebitIndicator, "creditDebitIndicator");
        requireNonNull(date, "date");
        requireNonNull(type, "type");
    }
}
