package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

record BalanceJson(AmountJson amount, String creditDebitIndicator, DateTimeValueJson date, BalanceTypeJson type) {
    BalanceJson {
        requireNonNull(amount, "amount");
        requireNonNull(creditDebitIndicator, "creditDebitIndicator");
        requireNonNull(date, "date");
        requireNonNull(type, "type");
    }
}
