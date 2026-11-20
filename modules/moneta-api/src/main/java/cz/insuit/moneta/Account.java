package cz.insuit.moneta;

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
        var identification = json.identification();
        var iban = identification == null ? null : identification.iban();
        return new Account(json.id(), iban, json.currency(), json.nameI18N());
    }
}
