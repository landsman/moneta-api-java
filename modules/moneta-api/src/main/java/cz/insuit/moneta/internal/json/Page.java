package cz.insuit.moneta.internal.json;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** A page of a list. The items stay JSON trees, so each one can be kept verbatim besides being bound. */
public sealed interface Page permits AccountsPage, TransactionsPage {
    public List<JsonNode> items();

    /** The page MONETA says it returned; null when it did not say. */
    @Nullable
    public Integer pageNumber();

    /** Null when MONETA did not say. */
    @Nullable
    public Integer pageCount();
}
