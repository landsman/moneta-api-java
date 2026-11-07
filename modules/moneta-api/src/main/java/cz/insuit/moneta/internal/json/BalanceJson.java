package cz.insuit.moneta.internal.json;

import static java.util.Objects.requireNonNull;

public record BalanceJson(Amount amount, String creditDebitIndicator, DateTimeValue date, BalanceType type) {
    public BalanceJson {
        requireNonNull(amount, "amount");
        requireNonNull(creditDebitIndicator, "creditDebitIndicator");
        requireNonNull(date, "date");
        requireNonNull(type, "type");
    }
}
