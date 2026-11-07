package example.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Money;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.junit.jupiter.api.Test;

/**
 * The extension inside a Quarkus application whose platform BOM picks the Jackson versions, the way a caller's
 * build does. Shared by every testing/quarkus/<version> project; each sets a different Quarkus version.
 */
@QuarkusTest
@QuarkusTestResource(FakeMoneta.class)
class MonetaInQuarkusTest {

    @Inject
    MonetaClient moneta;

    @Inject
    AccessToken token;

    @Test
    void aClientTheExtensionBuiltReadsTransactions() {
        var transactions = moneta.transactions(token, "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(
                new Money(new BigDecimal("-12.50"), Currency.getInstance("CZK")),
                transactions.getFirst().amount());
    }
}
