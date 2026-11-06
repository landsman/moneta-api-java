package cz.landsman.moneta;

import tools.jackson.databind.JsonNode;

/**
 * An account the token can see.
 *
 * @param id MONETA's id, the one {@link MonetaClient#transactions} takes
 */
public record Account(String id, String iban, String currency, String name) {

    static Account from(JsonNode node) {
        return new Account(
                node.path("id").asString(),
                node.path("identification").path("iban").asString(),
                node.path("currency").asString(),
                node.path("nameI18N").asString());
    }
}
