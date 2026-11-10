package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads accounts, their balances and their transactions. One instance serves any number of merchants: the token is passed
 * with each call and never kept, so it cannot end up in a {@code toString()} or a heap dump of the client.
 *
 * <p>Thread-safe; one per application is enough. {@link #close()} releases the HTTP client it made itself, not
 * one passed to {@link Builder#httpClient}.
 */
public final class MonetaClient implements AutoCloseable {

    /** MONETA's production host. */
    public static final URI PRODUCTION = URI.create("https://api.moneta.cz/");

    /** The JDK's own logging API, so the library adds no dependency; Spring Boot routes it into its log. */
    private static final System.Logger LOG = System.getLogger(MonetaClient.class.getName());

    private static final int PAGE_SIZE = 100;
    // ponytail: a backstop against a server that never sends an empty page; a million movements is far past it.
    private static final int MAX_PAGES = 10_000;
    private static final int MAX_BODY = 2_000;
    private static final JsonMapper JSON = JsonMapper.builder()
            // An amount is money: 0.1 must stay 0.1, not the nearest double.
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            // MONETA sends more than the records name, and may add keys at any time.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** What every call is sent through: {@link #inner}, wrapped by any {@link Builder#decorateHttpClient}. */
    private final HttpClient http;
    /** The client the library built or was given; the one {@link #close()} closes when the library built it. */
    private final HttpClient inner;

    private final boolean ownsHttp;
    private final URI baseUri;
    private final Duration requestTimeout;
    private final @Nullable Partner partner;

    /** A client for production, sending no partner headers. */
    public MonetaClient() {
        this(builder());
    }

    private MonetaClient(Builder builder) {
        if (builder.http != null && builder.configured) {
            throw new IllegalStateException("configureHttpClient changes the client the library builds;"
                    + " with httpClient(…) there is none, so configure the given one instead");
        }
        this.ownsHttp = builder.http == null;
        if (builder.http != null) {
            this.inner = builder.http;
        } else {
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
            builder.configure.accept(http);
            this.inner = http.build();
        }
        this.http = decorate(builder.decorator);
        this.baseUri = builder.baseUri;
        this.requestTimeout = builder.requestTimeout;
        this.partner = builder.partner;
    }

    /** A decorator that throws or returns null would otherwise leave the client built for it open until GC. */
    private HttpClient decorate(UnaryOperator<HttpClient> decorator) {
        @Nullable HttpClient decorated;
        try {
            decorated = decorator.apply(inner);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        if (decorated == null) {
            close();
            throw new NullPointerException("decorateHttpClient returned null");
        }
        return decorated;
    }

    /**
     * The endpoint a request of this client calls, as a template without the account id: {@code
     * /api/v4/vip/aisp/my/accounts/{accountId}/transactions}; {@code UNKNOWN} for a request it does not make. For a
     * metrics tag that stays low-cardinality, such as Micrometer's {@code uriMapper(MonetaClient::uriTemplate)}.
     * The query and any base-URI prefix are left out.
     */
    public static String uriTemplate(HttpRequest request) {
        return MonetaPaths.template(request.uri().getRawPath());
    }

    /** Starts a client for production; set only what differs. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void close() {
        // The inner client, not the decorated one: a tracing or metrics wrapper does not pass close() on.
        if (ownsHttp) {
            inner.close();
        }
    }

    /**
     * The accounts the token can see, all pages read.
     *
     * @throws MonetaException when MONETA refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public List<Account> accounts(AccessToken token) {
        return readAll(
                token,
                MonetaPaths.ACCOUNTS,
                Query.NONE,
                AccountsPageJson.class,
                node -> Account.from(bind(node, AccountJson.class)));
    }

    /**
     * The account's booked and available balance, as of the instant MONETA computed it.
     *
     * @throws IllegalArgumentException when the account id is blank or a dot segment
     * @throws MonetaException when MONETA refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public Balance balance(AccessToken token, String accountId) {
        var path = MonetaPaths.balance(accountId);
        var what = "GET /" + path;
        return Balance.from(what, get(token, what, path, BalancesJson.class));
    }

    /**
     * The money that came in: {@link #transactions(AccessToken, String, LocalDate, LocalDate)} with only {@link Transaction.Direction#CREDIT}, pending ones
     * included. MONETA has no such filter of its own that is known, so all movements are read and the debits
     * dropped here.
     *
     * @throws IllegalArgumentException when {@code to} is before {@code from}, or the account id is blank or a
     *     dot segment
     * @throws MonetaException when MONETA refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public List<Transaction> incoming(AccessToken token, String accountId, LocalDate from, LocalDate to) {
        return transactions(token, accountId, from, to).stream()
                .filter(t -> t.direction() == Transaction.Direction.CREDIT)
                .toList();
    }

    /**
     * A query over the account's movements, narrowed by chaining and read by {@link TransactionQuery#list()}:
     * {@code transactions(token, accountId).between(from, to).transfers().outgoing().list()}. Nothing is read
     * until {@code list()}.
     */
    public TransactionQuery transactions(AccessToken token, String accountId) {
        return new TransactionQuery(this, token, accountId);
    }

    /**
     * Every transaction on the account between two dates, both inclusive, all pages read, pending ones included
     * ({@link Transaction#booked()} tells them apart). MONETA filters a pending movement by its entered date; a
     * movement booked for a later day comes back from every range starting on or before that day, whatever
     * {@code to} says. .docs/moneta-api.md has the rest. All or nothing:
     * a failure on a later page discards the earlier ones, so read a long range month by month.
     *
     * @throws IllegalArgumentException when {@code to} is before {@code from}, or the account id is blank or a
     *     dot segment
     * @throws MonetaException when MONETA refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public List<Transaction> transactions(AccessToken token, String accountId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to " + to + " is before from " + from);
        }
        var query = Query.NONE.with("fromDate", from).with("toDate", to);
        return readAll(
                token,
                MonetaPaths.transactions(accountId),
                query,
                TransactionsPageJson.class,
                node -> Transaction.from(bind(node, TransactionJson.class), node.toString()));
    }

    /**
     * Reads page after page until one comes back empty or the last one by {@code pageCount} is read. The page
     * names are assumed, so a server that ignores {@code page} is caught rather than read as more data: a
     * {@code pageNumber} that is not the one asked for, or, without one, a page equal to the previous, fails
     * the read. A short page does not end it, so a server that caps {@code size} loses nothing.
     */
    private <T> List<T> readAll(
            AccessToken token,
            String path,
            Query query,
            Class<? extends PageJson> pageType,
            Function<JsonNode, T> map) {
        var all = new ArrayList<T>();
        var sized = query.with("size", PAGE_SIZE);
        List<JsonNode> previous = List.of();
        for (int page = 0; page < MAX_PAGES; page++) {
            var what = "GET /" + sized.with("page", page).appendTo(path);
            var body = get(token, what, sized.with("page", page).appendTo(path), pageType);
            var items = body.items();
            Integer number = body.pageNumber();
            if (number != null && number != page) {
                throw new MonetaException.InvalidResponse(
                        "GET /" + path + " asked for page " + page + " and got page " + number, null);
            }
            if (number == null && !items.isEmpty() && items.equals(previous)) {
                throw new MonetaException.InvalidResponse(
                        "GET /" + path + " returned the same items for page " + page + " as for the one before,"
                                + " so the server likely ignores the page parameter",
                        null);
            }
            for (var node : items) {
                try {
                    all.add(map.apply(node));
                } catch (MonetaException.InvalidResponse e) {
                    var reference = node.path("entryReference").asString("?");
                    throw new MonetaException.InvalidResponse(
                            what + ", item entryReference=" + reference + ": " + e.getMessage(), e);
                }
            }
            Integer count = body.pageCount();
            if (items.isEmpty() || (count != null && count > 0 && page + 1 >= count)) {
                return all;
            }
            previous = items;
        }
        throw new MonetaException.InvalidResponse("GET /" + path + " did not end within " + MAX_PAGES + " pages", null);
    }

    /** {@code what} names the request in messages: the query carries only dates and paging, the token is not in it. */
    private <T> T get(AccessToken token, String what, String pathAndQuery, Class<T> type) {
        var request = HttpRequest.newBuilder(baseUri.resolve(pathAndQuery))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + token.value())
                .header("Accept", "application/json");
        if (partner != null) {
            request.header("identification_number", partner.identificationNumber())
                    .header("business_name", partner.businessName())
                    .header("application_name", partner.applicationName());
        }
        HttpResponse<String> response;
        long started = System.nanoTime();
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | UncheckedIOException e) {
            // A decorated client may report a failed exchange unchecked; it is the same missing answer.
            log(what, null, millisSince(started), null, e.toString());
            throw new MonetaException.NoAnswer(what + " did not get an answer", e);
        } catch (InterruptedException e) {
            // Whoever owns the thread asked it to stop; that is not MONETA failing, so it is not a MonetaException
            // a retry would catch. Kotlin's coroutines read this type as cancellation.
            Thread.currentThread().interrupt();
            var cancelled = new CancellationException(what + " was interrupted");
            cancelled.initCause(e);
            throw cancelled;
        }
        int status = response.statusCode();
        var message = what + " answered " + status;
        // X-Request-Id is what MONETA's support asks for about one call. Never the token, never the body.
        log(
                what,
                status,
                millisSince(started),
                response.headers().firstValue("X-Request-Id").orElse(null),
                null);
        return switch (status / 100) {
            case 2 -> parse(what, () -> JSON.readValue(response.body(), type));
            default ->
                throw switch (status) {
                    case 401, 403 -> new MonetaException.InvalidToken(message, status, excerpt(response));
                    case 429 -> new MonetaException.RateLimited(message, retryAfter(response));
                    default -> new MonetaException.HttpError(message, status, excerpt(response), retryAfter(response));
                };
        };
    }

    /**
     * One call to MONETA as a JSON line, only when debug is on. Never the token, never the body. A map, not a record:
     * writing a bean makes databind 3.2 look for annotations newer than the {@code jackson-annotations} 2.21 a
     * Spring Boot 3.5 or Micronaut 4 BOM pins, and a native image would need it registered for reflection.
     *
     * @param requestId MONETA's {@code X-Request-Id}, what its support asks for about one call
     */
    private static void log(
            String request, @Nullable Integer status, long millis, @Nullable String requestId, @Nullable String error) {
        LOG.log(Level.DEBUG, () -> {
            var line = new LinkedHashMap<String, @Nullable Object>();
            line.put("request", request);
            line.put("status", status);
            line.put("millis", millis);
            line.put("requestId", requestId);
            line.put("error", error);
            return JSON.writeValueAsString(line);
        });
    }

    private static long millisSince(long nanos) {
        return (System.nanoTime() - nanos) / 1_000_000;
    }

    private static @Nullable String excerpt(HttpResponse<String> response) {
        var body = response.body();
        if (body == null || body.isEmpty()) {
            return null;
        }
        return body.length() > MAX_BODY ? body.substring(0, MAX_BODY) : body;
    }

    private static <T> T bind(JsonNode node, Class<T> type) {
        // readerFor, not treeToValue(JsonNode, Class): that overload is 3.1+, and Spring Boot 4.0.0-4.0.3 ship 3.0.
        return parse("a " + type.getSimpleName(), () -> JSON.readerFor(type).readValue(node));
    }

    /** Anything thrown while reading a body, or a JSON {@code null}, is MONETA's answer not having the expected shape. */
    private static <T> T parse(String what, Supplier<@Nullable T> read) {
        T value;
        try {
            value = read.get();
        } catch (RuntimeException e) {
            throw new MonetaException.InvalidResponse(what + " returned a body this library cannot read", e);
        }
        if (value == null) {
            throw new MonetaException.InvalidResponse(what + " returned null", null);
        }
        return value;
    }

    private static @Nullable Duration retryAfter(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Retry-After")
                // Seconds only; nine digits keep parseLong from overflowing. An HTTP date is ignored.
                .filter(value -> value.matches("\\d{1,9}"))
                .map(seconds -> Duration.ofSeconds(Long.parseLong(seconds)))
                .orElse(null);
    }

    /**
     * Sent as the {@code identification_number}, {@code business_name} and {@code application_name}
     * headers. Whether MONETA requires them, and whose values they are, is not documented publicly.
     */
    public record Partner(String identificationNumber, String businessName, String applicationName) {

        /**
         * @throws IllegalArgumentException when a value is blank or not printable ASCII: HTTP/1.1 headers cannot
         *     carry diacritics, and the JDK would refuse them on every call or send them as {@code ?}
         */
        public Partner {
            ascii("identificationNumber", identificationNumber);
            ascii("businessName", businessName);
            ascii("applicationName", applicationName);
        }

        private static void ascii(String name, String value) {
            requireNonNull(value, name);
            if (value.isBlank() || !value.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) {
                throw new IllegalArgumentException(name + " must be printable ASCII; transliterate diacritics");
            }
        }
    }

    /** Everything optional; {@code new MonetaClient()} is production with defaults. */
    public static final class Builder {

        private URI baseUri = PRODUCTION;
        private Duration requestTimeout = Duration.ofSeconds(30);
        private @Nullable Partner partner;
        private @Nullable HttpClient http;
        private UnaryOperator<HttpClient> decorator = UnaryOperator.identity();
        private Consumer<HttpClient.Builder> configure = http -> {};
        private boolean configured;

        private Builder() {}

        /**
         * MONETA's host, {@link #PRODUCTION} or the sandbox's. A path in it is kept.
         *
         * @throws IllegalArgumentException unless it is an absolute http(s) URI with a host and without a query
         *     or a fragment, so a wrong setting fails at startup rather than on the first call
         */
        public Builder baseUri(URI baseUri) {
            var scheme = baseUri.getScheme();
            boolean web = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
            if (!web
                    || baseUri.getHost() == null
                    || baseUri.getRawQuery() != null
                    || baseUri.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "baseUri must be an absolute http(s) URI with a host, without a query or a fragment: "
                                + baseUri);
            }
            var text = baseUri.toString();
            this.baseUri = text.endsWith("/") ? baseUri : URI.create(text + "/");
            return this;
        }

        /** The headers that name the registered application, if the API asks for them. */
        public Builder partner(Partner partner) {
            this.partner = partner;
            return this;
        }

        /**
         * Changes the HTTP client the library builds before it is built: a proxy, an {@code SSLContext} for mTLS
         * or a trust store, an executor. It starts with the library's settings (a 10-second connect timeout, no
         * redirects followed) and may override them; the library still owns and closes the client. Several apply
         * in the order added. Refused at {@link #build()} together with {@link #httpClient}, which leaves nothing
         * to build.
         */
        public Builder configureHttpClient(Consumer<HttpClient.Builder> configure) {
            requireNonNull(configure, "configure");
            this.configure = this.configure.andThen(configure);
            this.configured = true;
            return this;
        }

        /**
         * Wraps the HTTP client every call is sent through, for tracing or metrics: the decorator gets the client
         * the library built, or the one given to {@link #httpClient}, and returns one that delegates to it. It
         * must wrap, not replace: the library closes the client it built, not what comes back, so a wrapper that
         * does not pass {@code close()} on leaks nothing, and a different client returned instead would bypass
         * both that and the library's settings. Whatever the decorator itself allocates is the caller's. Several
         * apply once per {@link #build()}, the first added innermost. An unchecked {@code UncheckedIOException}
         * from the wrapper becomes {@link MonetaException.NoAnswer}; any other exception passes through as it is.
         */
        public Builder decorateHttpClient(UnaryOperator<HttpClient> decorator) {
            requireNonNull(decorator, "decorator");
            var before = this.decorator;
            this.decorator = client -> decorator.apply(before.apply(client));
            return this;
        }

        /**
         * For a proxy, custom TLS or a test server; the caller closes it. It replaces the client the library
         * would build, and with it what that one guarantees: no redirects followed, no cookies kept across
         * merchants' tokens, no {@code Authenticator}, a 10-second connect timeout. For tracing or metrics,
         * {@link #decorateHttpClient} keeps those.
         */
        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        /** How long one request may take, connecting included; 30 seconds unless set. */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /** The client; the builder can be reused for another. */
        public MonetaClient build() {
            return new MonetaClient(this);
        }
    }
}
