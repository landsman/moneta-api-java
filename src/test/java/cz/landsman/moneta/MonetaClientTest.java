package cz.landsman.moneta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MonetaClientTest {

    private static final String TOKEN = "secret-token";
    private static final String ACCOUNTS = "/api/v4/vip/aisp/my/accounts";
    private static final String TRANSACTIONS = ACCOUNTS + "/acc-1/transactions";
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 31);
    private static final Currency CZK = Currency.getInstance("CZK");

    private final List<HttpExchange> requests = new ArrayList<>();
    private HttpServer server;
    private URI base;
    private MonetaClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        client = MonetaClient.builder()
                .baseUri(base)
                .partner(new MonetaClient.Partner("12345678", "Example s.r.o.", "Example app"))
                .build();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsAccountsWithTheTokenAndPartnerHeaders() {
        answer(ACCOUNTS, 200, fixture("accounts.json"));

        var accounts = client.accounts(TOKEN);

        assertEquals(List.of(new Account("acc-1", "CZ6506000000000123456789", CZK, "Business account")), accounts);
        var headers = requests.getFirst().getRequestHeaders();
        assertEquals("Bearer " + TOKEN, headers.getFirst("Authorization"));
        assertEquals("12345678", headers.getFirst("identification_number"));
        assertEquals("Example s.r.o.", headers.getFirst("business_name"));
        assertEquals("Example app", headers.getFirst("application_name"));
    }

    @Test
    void readsEveryPageOfTransactions() {
        server.createContext(TRANSACTIONS, exchange -> {
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
        assertEquals(new Money(new BigDecimal("1234.10"), CZK), incoming.signedAmount());
        assertEquals(LocalDate.of(2026, 8, 1), incoming.bookingDate());
        assertEquals(new PaymentSymbols("2026000123", "0308", "0001"), incoming.symbols());
        assertTrue(incoming.raw().contains("\"entryReference\":\"trx-1\""));
        assertFalse(incoming.toString().contains("entryDetails"));

        var outgoing = transactions.get(1);
        assertEquals(Transaction.Status.PENDING, outgoing.status());
        assertEquals(new Money(new BigDecimal("-0.1"), CZK), outgoing.signedAmount());
        assertNull(outgoing.valueDate());
        assertEquals(new PaymentSymbols(null, null, null), outgoing.symbols());
    }

    @Test
    void withoutAPageCountReadsUntilAnEmptyPage() {
        server.createContext(ACCOUNTS, exchange -> {
            requests.add(exchange);
            var first = exchange.getRequestURI().getQuery().endsWith("page=0");
            respond(
                    exchange,
                    200,
                    first ? "{\"accounts\":[{\"id\":\"a\",\"currency\":\"CZK\"}]}" : "{\"accounts\":[]}");
        });

        assertEquals(1, client.accounts(TOKEN).size());
        assertEquals(2, requests.size());
    }

    @Test
    void aServerThatIgnoresThePageIsRefusedRatherThanReadThreeTimes() {
        answer(ACCOUNTS, 200, "{\"accounts\":[{\"id\":\"a\",\"currency\":\"CZK\"}],\"pageNumber\":0,\"pageCount\":3}");
        assertThrows(MonetaException.InvalidResponse.class, () -> client.accounts(TOKEN));
    }

    @Test
    void aServerThatCountsPagesFromOneIsRefused() {
        answer(ACCOUNTS, 200, "{\"accounts\":[{\"id\":\"a\",\"currency\":\"CZK\"}],\"pageNumber\":1,\"pageCount\":2}");
        assertThrows(MonetaException.InvalidResponse.class, () -> client.accounts(TOKEN));
    }

    @Test
    void theSamePageOverAndOverWithoutPageNumbersIsRefused() {
        answer(ACCOUNTS, 200, "{\"accounts\":[{\"id\":\"a\",\"currency\":\"CZK\"}]}");
        assertThrows(MonetaException.InvalidResponse.class, () -> client.accounts(TOKEN));
        assertEquals(2, requests.size());
    }

    @Test
    void aNullPageCountReadsUntilAnEmptyPage() {
        server.createContext(ACCOUNTS, exchange -> {
            requests.add(exchange);
            var first = exchange.getRequestURI().getQuery().endsWith("page=0");
            respond(
                    exchange,
                    200,
                    first
                            ? "{\"accounts\":[{\"id\":\"a\",\"currency\":\"CZK\"}],\"pageCount\":null}"
                            : "{\"accounts\":[],\"pageCount\":null}");
        });

        assertEquals(1, client.accounts(TOKEN).size());
    }

    @Test
    void anUnknownStatusIsKeptAsOther() {
        answer(TRANSACTIONS, 200, transaction("\"status\":\"XYZ\",\"creditDebitIndicator\":\"CRDT\""));
        assertEquals(
                Transaction.Status.OTHER,
                client.transactions(TOKEN, "acc-1", FROM, TO).getFirst().status());
    }

    @Test
    void anUnknownDirectionIsRefusedRatherThanGuessed() {
        answer(TRANSACTIONS, 200, transaction("\"status\":\"BOOK\",\"creditDebitIndicator\":\"XXXX\""));
        assertThrows(MonetaException.InvalidResponse.class, () -> client.transactions(TOKEN, "acc-1", FROM, TO));
    }

    @Test
    void aBodyOfTheWrongShapeIsAnInvalidResponse() {
        answer(ACCOUNTS, 200, "<html>gateway</html>");
        assertThrows(MonetaException.InvalidResponse.class, () -> client.accounts(TOKEN));

        server.removeContext(ACCOUNTS);
        answer(ACCOUNTS, 200, "{\"accounts\":[{\"currency\":\"CZK\"}]}");
        assertThrows(MonetaException.InvalidResponse.class, () -> client.accounts(TOKEN));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"accounts\":[{\"id\":\"a\",\"currency\":\"czk\"}]}",
                "{\"accounts\":[{\"id\":\"a\",\"currency\":\"XYZ\"}]}",
                "{\"accounts\":[null]}",
                "null",
                "{\"status\":\"maintenance\"}"
            })
    void anyAccountsBodyOfTheWrongShapeIsAnInvalidResponse(String body) {
        answer(ACCOUNTS, 200, body);
        assertThrows(MonetaException.InvalidResponse.class, () -> client.accounts(TOKEN));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"transactions\":[{\"entryReference\":\"t\",\"status\":\"BOOK\",\"creditDebitIndicator\":\"CRDT\","
                        + "\"amount\":{\"value\":1,\"currency\":\"\"}}]}",
                "{\"status\":\"maintenance\"}"
            })
    void anyTransactionsBodyOfTheWrongShapeIsAnInvalidResponse(String body) {
        answer(TRANSACTIONS, 200, body);
        assertThrows(MonetaException.InvalidResponse.class, () -> client.transactions(TOKEN, "acc-1", FROM, TO));
    }

    @Test
    void aBrokenItemNamesTheRequestThePageAndTheMovement() {
        server.createContext(TRANSACTIONS, exchange -> {
            var first = exchange.getRequestURI().getQuery().endsWith("page=0");
            respond(
                    exchange,
                    200,
                    first
                            ? fixture("transactions-page-0.json")
                            : transaction("\"status\":\"BOOK\",\"creditDebitIndicator\":\"XXXX\""));
        });

        var failed = assertThrows(
                MonetaException.InvalidResponse.class, () -> client.transactions(TOKEN, "acc-1", FROM, TO));
        var message = Objects.requireNonNull(failed.getMessage());
        assertTrue(message.contains("page=1"), message);
        assertTrue(message.contains("entryReference=t"), message);
        assertTrue(message.contains("'XXXX'"), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "99999999999999999999", "Wed, 21 Oct 2026 07:28:00 GMT"})
    void aRetryAfterThatIsNotSecondsIsStillRateLimited(String retryAfter) {
        server.createContext(ACCOUNTS, exchange -> {
            exchange.getResponseHeaders().add("Retry-After", retryAfter);
            respond(exchange, 429, "{}");
        });
        var throttled = assertThrows(MonetaException.RateLimited.class, () -> client.accounts(TOKEN));
        assertNull(throttled.retryAfter());
    }

    @Test
    void anErrorKeepsTheStartOfItsBodyOutOfTheMessage() {
        answer(ACCOUNTS, 400, "{\"error\":\"unknown parameter size\"}");
        var failed = assertThrows(MonetaException.HttpError.class, () -> client.accounts(TOKEN));
        assertEquals("{\"error\":\"unknown parameter size\"}", failed.body());
        assertFalse(Objects.requireNonNull(failed.getMessage()).contains("unknown parameter"));

        server.removeContext(ACCOUNTS);
        answer(ACCOUNTS, 400, "x".repeat(5_000));
        var long_ = assertThrows(MonetaException.HttpError.class, () -> client.accounts(TOKEN));
        assertEquals(2_000, Objects.requireNonNull(long_.body()).length());
    }

    @Test
    void aMaintenanceAnswerSaysWhenToComeBack() {
        server.createContext(ACCOUNTS, exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "120");
            respond(exchange, 503, "{}");
        });
        var failed = assertThrows(MonetaException.HttpError.class, () -> client.accounts(TOKEN));
        assertEquals(Duration.ofMinutes(2), failed.retryAfter());
    }

    @Test
    void aTokenWithANewlineIsRefusedWithoutBeingRepeated() {
        var refused = assertThrows(IllegalArgumentException.class, () -> client.accounts(TOKEN + "\n"));
        assertFalse(Objects.requireNonNull(refused.getMessage()).contains(TOKEN));
        assertTrue(requests.isEmpty());
    }

    @Test
    void tellsARefusedTokenFromThrottling() {
        answer(ACCOUNTS, 401, "{}");
        var refused = assertThrows(MonetaException.InvalidToken.class, () -> client.accounts(TOKEN));
        assertEquals(401, refused.status());
        assertFalse(Objects.requireNonNull(refused.getMessage()).contains(TOKEN));

        server.removeContext(ACCOUNTS);
        server.createContext(ACCOUNTS, exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "30");
            respond(exchange, 429, "{}");
        });
        var throttled = assertThrows(MonetaException.RateLimited.class, () -> client.accounts(TOKEN));
        assertEquals(Duration.ofSeconds(30), throttled.retryAfter());
    }

    @Test
    void reportsAnyOtherFailureWithItsStatus() {
        answer(ACCOUNTS, 503, "{}");
        var failed = assertThrows(MonetaException.HttpError.class, () -> client.accounts(TOKEN));
        assertEquals(503, failed.status());
    }

    @Test
    void aServerThatIsGoneIsNoAnswer() {
        server.stop(0);
        var failed = assertThrows(MonetaException.class, () -> client.accounts(TOKEN));
        assertInstanceOf(MonetaException.NoAnswer.class, failed);
    }

    @Test
    void keepsThePathOfABaseUriWithoutATrailingSlash() {
        answer("/sandbox" + ACCOUNTS, 200, fixture("accounts.json"));
        var sandbox =
                MonetaClient.builder().baseUri(URI.create(base + "sandbox")).build();

        assertEquals(1, sandbox.accounts(TOKEN).size());
    }

    @Test
    void refusesARangeThatEndsBeforeItStarts() {
        assertThrows(IllegalArgumentException.class, () -> client.transactions(TOKEN, "acc-1", TO, FROM));
    }

    private static String transaction(String statusAndDirection) {
        return "{\"transactions\":[{\"entryReference\":\"t\"," + statusAndDirection
                + ",\"amount\":{\"value\":1,\"currency\":\"CZK\"}}],\"pageCount\":1}";
    }

    private void answer(String path, int status, String body) {
        server.createContext(path, exchange -> {
            requests.add(exchange);
            respond(exchange, status, body);
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
