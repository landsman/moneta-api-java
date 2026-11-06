package cz.landsman.moneta;

import org.jspecify.annotations.Nullable;

/**
 * An account the token can see.
 *
 * @param id MONETA's id, the one {@link MonetaClient#transactions} takes
 */
public record Account(
        String id,
        @Nullable String iban,
        String currency,
        @Nullable String name) {

    static Account from(MonetaJson.AccountJson json) {
        var identification = json.identification();
        return new Account(
                json.id(), identification == null ? null : identification.iban(), json.currency(), json.nameI18N());
    }
}
