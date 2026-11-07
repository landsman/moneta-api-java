package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record AccountsPage(
        List<JsonNode> accounts,
        @Nullable Integer pageNumber,
        @Nullable Integer pageCount) implements Page {
    AccountsPage {
        // A 200 without the key is not an empty list: a maintenance page or a renamed key would read as no data.
        requireNonNull(accounts, "accounts");
    }

    @Override
    public List<JsonNode> items() {
        return accounts;
    }
}
