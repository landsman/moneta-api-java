package cz.landsman.moneta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonetaClientTest {

    private static final String TOKEN = "secret-token";
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);

    private final List<HttpExchange> requests = new ArrayList<>();
    private HttpServer server;
    private MonetaClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        client = new MonetaClient(base, new MonetaClient.Partner("12345678", "Example s.r.o.", "Example app"));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsAccountsWithTheTokenAndPartnerHeaders() {
        answer("/api/v4/vip/aisp/my/accounts", 200, "accounts.json");

        var accounts = client.accounts(TOKEN);

        assertEquals(List.of(new Account("acc-1", "CZ6506000000000123456789", "CZK", "Business account")), accounts);
        var headers = requests.getFirst().getRequestHeaders();
        assertEquals("Bearer " + TOKEN, headers.getFirst("Authorization"));
        assertEquals("12345678", headers.getFirst("identification_number"));
        assertEquals("Example s.r.o.", headers.getFirst("business_name"));
        assertEquals("Example app", headers.getFirst("application_name"));
    }

    @Test
    void readsEveryPageOfTransactions() {
        server.createContext("/api/v4/vip/aisp/my/accounts/acc-1/transactions", exchange -> {
            requests.add(exchange);
            var page = exchange.getRequestURI().getQuery().endsWith("page=0") ? 0 : 1;
            respond(exchange, 200, fixture("transactions-page-" + page + ".json"));
        });

        var transactions = client.transactions(TOKEN, "acc-1", FROM, TO);

        assertEquals(2, transactions.size());
        assertEquals(
                "fromDate=2026-08-01&toDate=2026-08-31&size=100&page=0",
                requests.get(0).getRequestURI().getQuery());
        assertEquals(
                "fromDate=2026-08-01&toDate=2026-08-31&size=100&page=1",
                requests.get(1).getRequestURI().getQuery());

        var incoming = transactions.get(0);
        assertEquals("trx-1", incoming.entryReference());
        assertTrue(incoming.booked());
        assertEquals(new BigDecimal("1234.10"), incoming.signedAmount());
        assertEquals("CZK", incoming.currency());
        assertEquals(LocalDate.of(2026, 8, 1), incoming.bookingDate());
        assertEquals(new PaymentSymbols("2026000123", "0308", "0001"), incoming.symbols());
        assertTrue(incoming.raw().contains("\"entryReference\":\"trx-1\""));

        var outgoing = transactions.get(1);
        assertFalse(outgoing.booked());
        assertEquals(new BigDecimal("-0.1"), outgoing.signedAmount());
        assertEquals(null, outgoing.valueDate());
        assertEquals(new PaymentSymbols(null, null, null), outgoing.symbols());
    }

    @Test
    void tellsARefusedTokenFromThrottling() {
        answer("/api/v4/vip/aisp/my/accounts", 401, null);
        var refused = assertThrows(MonetaException.InvalidToken.class, () -> client.accounts(TOKEN));
        assertEquals(401, refused.status());
        assertFalse(Objects.requireNonNull(refused.getMessage()).contains(TOKEN));

        server.removeContext("/api/v4/vip/aisp/my/accounts");
        answer("/api/v4/vip/aisp/my/accounts", 429, null);
        assertThrows(MonetaException.RateLimited.class, () -> client.accounts(TOKEN));
    }

    @Test
    void reportsAnyOtherFailureWithItsStatus() {
        answer("/api/v4/vip/aisp/my/accounts", 503, null);
        var failed = assertThrows(MonetaException.class, () -> client.accounts(TOKEN));
        assertEquals(503, failed.status());
    }

    private void answer(String path, int status, @Nullable String fixture) {
        server.createContext(path, exchange -> {
            requests.add(exchange);
            respond(exchange, status, fixture == null ? "{}" : fixture(fixture));
        });
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String fixture(String name) {
        try (var in = Objects.requireNonNull(MonetaClientTest.class.getResourceAsStream("/moneta/" + name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
