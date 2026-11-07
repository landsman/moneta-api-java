package example.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.inject.ConfigurationException;
import com.sun.net.httpserver.HttpServer;
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.MonetaException;
import cz.insuit.moneta.Money;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import scala.jdk.javaapi.FutureConverters;

/**
 * The module inside a Play application, found through the jar's reference.conf and not through bindings of the
 * test's own, with the Jackson Play brings. Shared by every testing/play/<version> project.
 */
class MonetaInPlayTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void startMoneta() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
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
        base = "http://" + server.getAddress().getHostString() + ":"
                + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopMoneta() {
        server.stop(0);
    }

    @Test
    void aClientTheModuleBuiltReadsTransactions() throws Exception {
        var app = new GuiceApplicationBuilder()
                .configure("moneta.base-uri", base)
                .configure("moneta.token", "t")
                .build();
        try {
            var moneta = app.injector().instanceOf(MonetaClient.class);
            var token = app.injector().instanceOf(AccessToken.class);

            var transactions = moneta.transactions(token, "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

            assertEquals(
                    new Money(new BigDecimal("-12.50"), Currency.getInstance("CZK")),
                    transactions.getFirst().amount());
            assertSame(moneta, app.injector().instanceOf(MonetaClient.class));
        } finally {
            stop(app);
        }
    }

    @Test
    void noTokenNoAccessToken() throws Exception {
        var app = new GuiceApplicationBuilder().build();
        try {
            assertThrows(ConfigurationException.class, () -> app.injector().instanceOf(AccessToken.class));
        } finally {
            stop(app);
        }
    }

    @Test
    void stoppingTheApplicationClosesTheClient() throws Exception {
        var app =
                new GuiceApplicationBuilder().configure("moneta.base-uri", base).build();
        var moneta = app.injector().instanceOf(MonetaClient.class);
        stop(app);

        // The JDK client refuses to send once closed; the library reports that as no answer.
        assertThrows(
                MonetaException.NoAnswer.class,
                () -> moneta.transactions(
                        new AccessToken("t"), "acc-1", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    /** Play 3.0 and 2.9 pin Jackson 2.14, below the jackson-annotations 2.21 the client brings. */
    @Test
    void playsOwnJsonStillWorks() {
        var pair = new Pair("a", 1);
        assertEquals(pair, Json.fromJson(Json.toJson(pair), Pair.class));
    }

    record Pair(String name, int count) {}

    private static void stop(Application app) throws Exception {
        FutureConverters.asJava(app.asScala().stop()).toCompletableFuture().get();
    }
}
