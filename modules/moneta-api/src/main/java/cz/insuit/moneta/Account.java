package cz.insuit.moneta;

import cz.insuit.moneta.internal.json.AccountJson;
import cz.insuit.moneta.internal.json.Identification;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * An account the token can see.
 *
 * @param id MONETA's id, the one {@link MonetaClient#transactions} takes
 */
public record Account(
        String id,
        @Nullable String iban,
        Currency currency,
        @Nullable String name) {

    static Account from(AccountJson json) {
        var iban = json.identification() instanceof Identification(var value) ? value : null;
        return new Account(json.id(), iban, json.currency(), json.nameI18N());
    }
}
