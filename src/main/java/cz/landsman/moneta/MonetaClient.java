package cz.landsman.moneta;

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
    public List<Account> accounts(String token) {
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
    public List<Transaction> transactions(String token, String accountId, LocalDate from, LocalDate to) {
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
     * Reads page after page until one comes back empty or the last one by {@code pageCount} is read. A
     * missing {@code pageCount} reads until the empty page, rather than stopping after the first.
     */
    private <T> List<T> readAll(
            String token,
            String path,
            Query query,
            Class<? extends MonetaJson.Page> pageType,
            Function<JsonNode, T> map) {
        var all = new ArrayList<T>();
        var sized = query.with("size", PAGE_SIZE);
        for (int page = 0; ; page++) {
            var body = get(token, path, sized.with("page", page), pageType);
            for (var node : body.items()) {
                all.add(map.apply(node));
            }
            boolean last = body.pageCount() > 0 && page + 1 >= body.pageCount();
            if (body.items().isEmpty() || last) {
                return all;
            }
        }
    }

    private <T> T get(String token, String path, Query query, Class<T> type) {
        var pathAndQuery = query.appendTo(path);
        // The query carries only dates and paging, so the path is safe to put in a message; the token is not.
        var what = "GET /" + pathAndQuery;
        var request = HttpRequest.newBuilder(baseUri.resolve(pathAndQuery))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + checked(token))
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
                    case 401, 403 -> new MonetaException.InvalidToken(message, status);
                    case 429 -> new MonetaException.RateLimited(message, retryAfter(response));
                    default -> new MonetaException.HttpError(message, status);
                };
        };
    }

    private static <T> T bind(JsonNode node, Class<T> type) {
        return parse("a " + type.getSimpleName(), () -> JSON.treeToValue(node, type));
    }

    /** Anything thrown while reading a body is MONETA's answer not having the expected shape. */
    private static <T> T parse(String what, Supplier<T> read) {
        try {
            return read.get();
        } catch (RuntimeException e) {
            throw new MonetaException.InvalidResponse(what + " returned a body this library cannot read", e);
        }
    }

    /**
     * The JDK's own header check would quote the whole value in its exception, token included, so a stray
     * newline from a file or an environment variable is refused here, without repeating it.
     */
    private static String checked(String token) {
        boolean printable = !token.isEmpty() && token.chars().allMatch(c -> c > 0x20 && c < 0x7F);
        if (!printable) {
            throw new IllegalArgumentException("The token is empty or holds a space or a control character");
        }
        return token;
    }

    private static @Nullable Duration retryAfter(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Retry-After")
                .filter(value -> value.chars().allMatch(Character::isDigit))
                .map(seconds -> Duration.ofSeconds(Long.parseLong(seconds)))
                .orElse(null);
    }

    /**
     * Sent as the {@code identification_number}, {@code business_name} and {@code application_name}
     * headers. Whether MONETA requires them, and whose values they are, is not documented publicly.
     */
    public record Partner(String identificationNumber, String businessName, String applicationName) {}

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
