package cz.insuit.moneta;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * MONETA's JSON, one record per object, named exactly as MONETA names the keys. Jackson binds them by those
 * names, so a key is written once, here, and the rest of the library reads typed fields. Unknown keys are
 * ignored. A key the library relies on is checked in the compact constructor, so a missing one fails at
 * binding, as {@link MonetaException.InvalidResponse}, and not as a null three calls later.
 *
 * <p>This type: a page of a list. The items stay JSON trees, so each one can be kept verbatim besides
 * being bound.
 */
sealed interface Page permits AccountsPage, TransactionsPage {
    List<JsonNode> items();

    /** The page MONETA says it returned; null when it did not say. */
    @Nullable
    Integer pageNumber();

    /** Null when MONETA did not say. */
    @Nullable
    Integer pageCount();
}
