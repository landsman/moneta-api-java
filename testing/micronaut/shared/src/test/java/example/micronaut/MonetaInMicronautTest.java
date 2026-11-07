package example.micronaut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;

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

    /**
     * What the module README promises: with slf4j-jdk-platform-logging on the classpath, the client's DEBUG line
     * reaches Logback, switched on by logger.levels alone. Micronaut itself does not bridge java.util.logging.
     */
    @Test
    void theClientsDebugLineReachesLogbackThroughLoggerLevels() throws IOException {
        var logback = (LoggerContext) LoggerFactory.getILoggerFactory();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            var body = "{\"accounts\": []}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try (var context = ApplicationContext.run(Map.of(
                "moneta.base-uri",
                "http://localhost:" + server.getAddress().getPort(),
                "logger.levels.cz.insuit.moneta",
                "DEBUG"))) {
            // After the start: Micronaut resets Logback from logback.xml while applying logger.levels.
            var lines = new ListAppender<ILoggingEvent>();
            lines.start();
            logback.getLogger("cz.insuit.moneta").addAppender(lines);

            context.getBean(MonetaClient.class).accounts(new AccessToken("t"));

            assertTrue(
                    lines.list.stream()
                            .anyMatch(line -> line.getLoggerName().equals(MonetaClient.class.getName())
                                    && line.getLevel() == Level.DEBUG),
                    "no DEBUG line from the client reached Logback");
        } finally {
            server.stop(0);
        }
    }
}
