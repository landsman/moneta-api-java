package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.util.Currency;
import org.jspecify.annotations.Nullable;

record AccountJson(
        String id,
        Currency currency,
        @Nullable String nameI18N,
        @Nullable IdentificationJson identification) {
    AccountJson {
        requireNonNull(id, "id");
        requireNonNull(currency, "currency");
    }
}
