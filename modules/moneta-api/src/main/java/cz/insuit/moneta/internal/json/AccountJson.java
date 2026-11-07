package cz.insuit.moneta.internal.json;

import static java.util.Objects.requireNonNull;

import java.util.Currency;
import org.jspecify.annotations.Nullable;

public record AccountJson(
        String id,
        Currency currency,
        @Nullable String nameI18N,
        @Nullable Identification identification) {
    public AccountJson {
        requireNonNull(id, "id");
        requireNonNull(currency, "currency");
    }
}
