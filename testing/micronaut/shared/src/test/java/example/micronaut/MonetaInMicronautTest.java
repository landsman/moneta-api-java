package example.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Money;
import cz.insuit.moneta.micronaut.MonetaProperties;
import io.micronaut.context.ApplicationContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The module, compiled against Micronaut 4.10, inside a Micronaut application whose BOM picks the Jackson versions.
 * Shared by every testing/micronaut/<version> project; each sets a different Micronaut version.
 */
class MonetaInMicronautTest {

    @Test
    void aClientTheContextBuiltReadsTransactions() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes;
            try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/transactions.json"))) {
                bytes = in.readAllBytes();
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        var base = "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + "/";
        try (var context = ApplicationContext.run(
                Map.of("moneta.base-uri", base, "moneta.token", "t", "moneta.request-timeout", "5s"))) {
            var moneta = context.getBean(MonetaClient.class);
            var token = context.getBean(AccessToken.class);

            var transactions = moneta.transactions(token, "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

            assertEquals(
                    new Money(new BigDecimal("-12.50"), Currency.getInstance("CZK")),
                    transactions.getFirst().amount());
            assertEquals(
                    Duration.ofSeconds(5),
                    context.getBean(MonetaProperties.class).requestTimeout());
        } finally {
            server.stop(0);
        }
    }
}
