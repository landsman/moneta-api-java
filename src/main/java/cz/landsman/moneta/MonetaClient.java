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
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationFeature;
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
        this(PRODUCTION, null);
    }

    /**
     * @param baseUri MONETA's host, {@link #PRODUCTION} or the sandbox's; ends with a slash
     * @param partner the headers that name the registered application, if the API asks for them
     */
    public MonetaClient(URI baseUri, @Nullable Partner partner) {
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUri = baseUri;
        this.partner = partner;
    }

    /** The accounts the token can see. */
    public List<Account> accounts(String token) {
        var accounts = new ArrayList<Account>();
        for (var json : get(token, MonetaPaths.ACCOUNTS, Query.NONE, MonetaJson.AccountsPage.class)
                .all()) {
            accounts.add(Account.from(json));
        }
        return accounts;
    }

    /**
     * Every transaction on the account between two dates, inclusive, all pages read. Pending ones are
     * included; {@link Transaction#booked()} tells them apart.
     */
    public List<Transaction> transactions(String token, String accountId, LocalDate from, LocalDate to) {
        var path = MonetaPaths.transactions(accountId);
        var query = Query.NONE.with("fromDate", from).with("toDate", to).with("size", PAGE_SIZE);
        var transactions = new ArrayList<Transaction>();
        for (int page = 0; ; page++) {
            var body = get(token, path, query.with("page", page), MonetaJson.TransactionsPage.class);
            for (var node : body.all()) {
                transactions.add(
                        Transaction.from(JSON.treeToValue(node, MonetaJson.TransactionJson.class), node.toString()));
            }
            if (page + 1 >= body.pageCount()) {
                return transactions;
            }
        }
    }

    private <T> T get(String token, String path, Query query, Class<T> type) {
        var pathAndQuery = query.appendTo(path);
        var request = HttpRequest.newBuilder(baseUri.resolve(pathAndQuery))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if (partner != null) {
            request.header("identification_number", partner.identificationNumber())
                    .header("business_name", partner.businessName())
                    .header("application_name", partner.applicationName());
        }
        // The query carries only dates and paging, so the path is safe to put in a message; the token is not.
        var what = "GET /" + pathAndQuery;
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MonetaException(what + " did not get an answer", 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MonetaException(what + " was interrupted", 0, e);
        }
        int status = response.statusCode();
        if (status / 100 == 2) {
            return JSON.readValue(response.body(), type);
        }
        var message = what + " answered " + status;
        throw switch (status) {
            case 401, 403 -> new MonetaException.InvalidToken(message, status);
            case 429 -> new MonetaException.RateLimited(message);
            default -> new MonetaException(message, status, null);
        };
    }

    /**
     * Sent as the {@code identification_number}, {@code business_name} and {@code application_name}
     * headers. Whether MONETA requires them, and whose values they are, is not documented publicly.
     */
    public record Partner(String identificationNumber, String businessName, String applicationName) {}
}
