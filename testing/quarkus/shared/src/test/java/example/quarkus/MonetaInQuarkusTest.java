package example.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Money;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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
                transactions.get(0).amount());
    }

    /**
     * What the extension README promises: quarkus.log.category switches the client's DEBUG line on. Quarkus is the
     * java.util.logging implementation, so System.Logger lands in its log without a bridge.
     */
    @Test
    void theClientsDebugLineReachesTheLogThroughALogCategory() {
        var lines = new CopyOnWriteArrayList<LogRecord>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        var logger = Logger.getLogger(MonetaClient.class.getName());
        logger.addHandler(handler);
        try {
            moneta.transactions(token, "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(
                lines.stream().anyMatch(line -> line.getLevel().intValue() <= Level.FINE.intValue()),
                "no DEBUG line from the client reached the log");
    }
}
