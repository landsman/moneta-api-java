package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

record TransactionsPageJson(
        List<JsonNode> transactions,
        @Nullable Integer pageNumber,
        @Nullable Integer pageCount) implements PageJson {
    TransactionsPageJson {
        requireNonNull(transactions, "transactions");
    }

    @Override
    public List<JsonNode> items() {
        return transactions;
    }
}
