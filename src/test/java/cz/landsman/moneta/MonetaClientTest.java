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
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MonetaClientTest {

    private static final AccessToken TOKEN = new AccessToken("secret-token");
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
    void start() throws IOException, URISyntaxException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        var address = server.getAddress();
        base = new URI("http", null, address.getHostString(), address.getPort(), "/", null, null);
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
        assertEquals("Bearer " + TOKEN.value(), headers.getFirst("Authorization"));
        assertEquals("application/json", headers.getFirst("Accept"));
        assertEquals("12345678", headers.getFirst("identification_number"));
        assertEquals("Example s.r.o.", headers.getFirst("business_name"));
        assertEquals("Example app", headers.getFirst("application_name"));
    }

    @Test
    void readsEveryPageOfTransactions() {
        answerBothPages();

        var transactions = client.transactions(TOKEN, "acc-1", FROM, TO);

        assertEquals(
                List.of("trx-1", "trx-2"),
                transactions.stream().map(Transaction::entryReference).toList());
        assertEquals(
                "fromDate=2026-08-01&toDate=2026-08-31&size=100&page=0",
                requests.get(0).getRequestURI().getQuery());
        assertEquals(
                "fromDate=2026-08-01&toDate=2026-08-31&size=100&page=1",
                requests.get(1).getRequestURI().getQuery());
    }

    @Test
    void mapsEachMovement() {
        answerBothPages();

        var transactions = client.transactions(TOKEN, "acc-1", FROM, TO);

        var incoming = transactions.get(0);
        assertTrue(incoming.booked());
        assertEquals(Transaction.Direction.CREDIT, incoming.direction());
        assertEquals(new Money(new BigDecimal("1234.10"), CZK), incoming.amount());
        assertEquals(LocalDate.of(2026, 8, 1), incoming.bookingDate());
        assertEquals(LocalDate.of(2026, 8, 3), incoming.valueDate());
        assertEquals(new PaymentSymbols("2026000123", "0308", "0001"), incoming.symbols());

        var outgoing = transactions.get(1);
        assertFalse(outgoing.booked());
        assertEquals(Transaction.Status.PENDING, outgoing.status());
        assertEquals(Transaction.Direction.DEBIT, outgoing.direction());
        assertEquals(new Money(new BigDecimal("-0.10"), CZK), outgoing.amount());
        assertEquals(LocalDate.of(2026, 8, 2), outgoing.bookingDate());
        assertNull(outgoing.valueDate());
        assertEquals(new PaymentSymbols(null, null, null), outgoing.symbols());
    }

    @Test
    void keepsTheCounterpartyInRawButOutOfToString() {
        answerBothPages();

        var incoming = client.transactions(TOKEN, "acc-1", FROM, TO).getFirst();

        assertTrue(incoming.raw().contains("Jan Novak"));
        assertTrue(incoming.toString().contains("entryReference=trx-1"), incoming.toString());
        assertFalse(incoming.toString().contains("Jan Novak"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"entryReference", "status", "creditDebitIndicator", "amount"})
    void aMovementWithoutARequiredKeyIsAnInvalidResponse(String key) {
        var movement = new LinkedHashMap<String, String>();
        movement.put("entryReference", "\"t\"");
        movement.put("status", "\"BOOK\"");
        movement.put("creditDebitIndicator", "\"CRDT\"");
        movement.put("amount", "{\"value\":1,\"currency\":\"CZK\"}");
        movement.remove(key);
        var json = movement.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{\"transactions\":[{", "}],\"pageCount\":1}"));
        answer(TRANSACTIONS, 200, json);

        assertThrows(MonetaException.InvalidResponse.class, () -> client.transactions(TOKEN, "acc-1", FROM, TO));
    }

    @Test
    void anAccountWithoutACurrencyIsAnInvalidResponse() {
        answer(ACCOUNTS, 200, "{\"accounts\":[{\"id\":\"a\"}],\"pageCount\":1}");
        assertThrows(MonetaException.InvalidResponse.class, () -> client.accounts(TOKEN));
    }

    @Test
    void aRangeOfOneDayIsAllowed() {
        answer(TRANSACTIONS, 200, "{\"transactions\":[]}");
        assertEquals(List.of(), client.transactions(TOKEN, "acc-1", FROM, FROM));
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
    void anAmountADoubleCannotHoldArrivesExact() {
        answer(
                TRANSACTIONS,
                200,
                "{\"transactions\":[{\"entryReference\":\"t\",\"status\":\"BOOK\",\"creditDebitIndicator\":\"CRDT\","
                        + "\"amount\":{\"value\":1234567890123456.78,\"currency\":\"CZK\"}}],\"pageCount\":1}");
        assertEquals(
                new BigDecimal("1234567890123456.78"),
                client.transactions(TOKEN, "acc-1", FROM, TO)
                        .getFirst()
                        .amount()
                        .amount());
    }

    @Test
    void amountsAreSignedSoTheyAddUp() {
        answerBothPages();

        var sum = client.transactions(TOKEN, "acc-1", FROM, TO).stream()
                .map(t -> t.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("1234.00"), sum);
    }

    @Test
    void aNegativeAmountBesideADebitIsRefusedRatherThanSignedTwice() {
        answer(
                TRANSACTIONS,
                200,
                "{\"transactions\":[{\"entryReference\":\"t\",\"status\":\"BOOK\",\"creditDebitIndicator\":\"DBIT\","
                        + "\"amount\":{\"value\":-100.00,\"currency\":\"CZK\"}}]}");
        assertThrows(MonetaException.InvalidResponse.class, () -> client.transactions(TOKEN, "acc-1", FROM, TO));
    }

    @Test
    void partnerValuesHaveToBeAsciiOrTheyCouldNotBeSent() {
        assertThrows(IllegalArgumentException.class, () -> new MonetaClient.Partner("1", "Pekařství Novák", "app"));
        assertThrows(IllegalArgumentException.class, () -> new MonetaClient.Partner("1", "Kávárna", "app"));
        assertThrows(IllegalArgumentException.class, () -> new MonetaClient.Partner("1", " ", "app"));
        new MonetaClient.Partner("1", "Example s.r.o.", "app");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void aRefusedTokenIsInvalidToken(int status) {
        answer(ACCOUNTS, status, "{}");
        var refused = assertThrows(MonetaException.InvalidToken.class, () -> client.accounts(TOKEN));
        assertEquals(status, refused.status());
        assertFalse(Objects.requireNonNull(refused.getMessage()).contains(TOKEN.value()));
    }

    @Test
    void throttlingSaysHowLongToWait() {
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
    void anInterruptedThreadIsCancelledAndStaysInterrupted() {
        server.createContext(ACCOUNTS, exchange -> {
            sleep(Duration.ofSeconds(1));
            respond(exchange, 200, fixture("accounts.json"));
        });

        Thread.currentThread().interrupt();
        try {
            var cancelled = assertThrows(CancellationException.class, () -> client.accounts(TOKEN));
            assertInstanceOf(InterruptedException.class, cancelled.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void usesTheHttpClientItIsGiven() {
        server.createContext("/moved/", exchange -> {
            exchange.getResponseHeaders()
                    .add("Location", base.resolve(ACCOUNTS.substring(1)).toString());
            respond(exchange, 302, "");
        });
        answer(ACCOUNTS, 200, fixture("accounts.json"));
        var following = MonetaClient.builder()
                .baseUri(base.resolve("moved/"))
                .httpClient(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build())
                .build();

        assertEquals(1, following.accounts(TOKEN).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", ".."})
    void refusesAnAccountIdThatIsNotOneSegment(String accountId) {
        assertThrows(IllegalArgumentException.class, () -> client.transactions(TOKEN, accountId, FROM, TO));
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"api.moneta.cz", "ftp://h/", "https://h/s?x=1", "https://h/s#f"})
    void refusesABaseUriItCouldNotCall(String uri) {
        var builder = MonetaClient.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.baseUri(URI.create(uri)));
    }

    @Test
    void givesUpAfterTheRequestTimeout() {
        server.createContext(ACCOUNTS, exchange -> {
            sleep(Duration.ofSeconds(1));
            respond(exchange, 200, fixture("accounts.json"));
        });
        var impatient = MonetaClient.builder()
                .baseUri(base)
                .requestTimeout(Duration.ofMillis(200))
                .build();

        long start = System.nanoTime();
        var failed = assertThrows(MonetaException.NoAnswer.class, () -> impatient.accounts(TOKEN));
        assertInstanceOf(HttpTimeoutException.class, failed.getCause());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(900)) < 0);
    }

    @Test
    void closesOnlyTheHttpClientItMade() {
        answer(ACCOUNTS, 200, fixture("accounts.json"));
        var own = MonetaClient.builder().baseUri(base).build();
        own.close();
        assertThrows(MonetaException.NoAnswer.class, () -> own.accounts(TOKEN));

        var http = HttpClient.newHttpClient();
        MonetaClient.builder().baseUri(base).httpClient(http).build().close();
        var reused = MonetaClient.builder().baseUri(base).httpClient(http).build();
        assertEquals(1, reused.accounts(TOKEN).size());
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

    private void answerBothPages() {
        server.createContext(TRANSACTIONS, exchange -> {
            requests.add(exchange);
            var page = exchange.getRequestURI().getQuery().endsWith("page=0") ? 0 : 1;
            respond(exchange, 200, fixture("transactions-page-" + page + ".json"));
        });
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
