package cz.landsman.moneta;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads accounts and their transactions. One instance serves any number of merchants: the token is passed
 * with each call and never kept, so it cannot end up in a {@code toString()} or a heap dump of the client.
 *
 * <p>Thread-safe.
 */
public final class MonetaClient {

    /** MONETA's production host. */
    public static final URI PRODUCTION = URI.create("https://api.moneta.cz/");

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

    private final HttpClient http;
    private final URI baseUri;
    private final @Nullable Partner partner;

    /** A client for production, sending no partner headers. */
    public MonetaClient() {
        this(builder());
    }

    private MonetaClient(Builder builder) {
        this.http = builder.http != null
                ? builder.http
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUri = builder.baseUri;
        this.partner = builder.partner;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The accounts the token can see, all pages read. */
    public List<Account> accounts(AccessToken token) {
        return readAll(
                token,
                MonetaPaths.ACCOUNTS,
                Query.NONE,
                MonetaJson.AccountsPage.class,
                node -> Account.from(bind(node, MonetaJson.AccountJson.class)));
    }

    /**
     * Every transaction on the account between two dates, inclusive, all pages read. Pending ones are
     * included; {@link Transaction#booked()} tells them apart.
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
                MonetaJson.TransactionsPage.class,
                node -> Transaction.from(bind(node, MonetaJson.TransactionJson.class), node.toString()));
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
            Class<? extends MonetaJson.Page> pageType,
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
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token.value())
                .header("Accept", "application/json");
        if (partner != null) {
            request.header("identification_number", partner.identificationNumber())
                    .header("business_name", partner.businessName())
                    .header("application_name", partner.applicationName());
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MonetaException.NoAnswer(what + " did not get an answer", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MonetaException.NoAnswer(what + " was interrupted", e);
        }
        int status = response.statusCode();
        var message = what + " answered " + status;
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
        private @Nullable Partner partner;
        private @Nullable HttpClient http;

        private Builder() {}

        /** MONETA's host, {@link #PRODUCTION} or the sandbox's. A path in it is kept. */
        public Builder baseUri(URI baseUri) {
            var text = baseUri.toString();
            this.baseUri = text.endsWith("/") ? baseUri : URI.create(text + "/");
            return this;
        }

        /** The headers that name the registered application, if the API asks for them. */
        public Builder partner(Partner partner) {
            this.partner = partner;
            return this;
        }

        /** For a proxy, custom TLS or a test server. Its connect timeout is the caller's. */
        public Builder httpClient(HttpClient http) {
            this.http = http;
            return this;
        }

        public MonetaClient build() {
            return new MonetaClient(this);
        }
    }
}
