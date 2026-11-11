package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import org.jspecify.annotations.Nullable;

/**
 * A {@link MonetaClient} with one holder's token bound, from {@link MonetaClient#with(AccessToken)}:
 *
 * <pre>{@code
 * var me = moneta.with(token);
 * me.transactions().between(from, to).cards().list();
 * me.balance();
 * }</pre>
 *
 * Holds the client and the token only, so making one per request costs nothing. The calls without an account id
 * read the only account the token opens and remember its id, so a holder kept between calls asks for it once.
 * Safe to share between threads.
 */
public final class AccountHolder {

    private final MonetaClient client;
    private final AccessToken token;
    // ponytail: remembered for this holder's life; an account closed meanwhile surfaces as MONETA's own error
    private volatile @Nullable String onlyAccountId;

    AccountHolder(MonetaClient client, AccessToken token) {
        this.client = requireNonNull(client);
        this.token = requireNonNull(token, "token");
    }

    /** The movements on the only account the token opens; see {@link TransactionQuery}. */
    public TransactionQuery transactions() {
        return new TransactionQuery(client, token, this::onlyAccountId);
    }

    /** The movements on this account; see {@link TransactionQuery}. */
    public TransactionQuery transactions(String accountId) {
        return client.transactions(token, accountId);
    }

    /**
     * The only account's balance.
     *
     * @throws IllegalStateException when the token opens other than exactly one account
     * @throws MonetaException when MONETA refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public Balance balance() {
        return client.balance(token, onlyAccountId());
    }

    /** As {@link MonetaClient#balance(AccessToken, String)}. */
    public Balance balance(String accountId) {
        return client.balance(token, accountId);
    }

    /** As {@link MonetaClient#accounts(AccessToken)}. */
    public List<Account> accounts() {
        return client.accounts(token);
    }

    /** As {@link MonetaClient#tokenExpiry(AccessToken)}. */
    public Instant tokenExpiry() {
        return client.tokenExpiry(token);
    }

    /**
     * The account with this IBAN, compared without spaces and case, so {@code cz02 0600 …} as printed matches; an
     * account without an IBAN never does. Reads {@link #accounts()} on every call and remembers nothing, so an
     * account the token stopped seeing shows on the next one.
     *
     * @return null when the token does not see it: revoked for that account, or another holder's token
     * @throws MonetaException when MONETA refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public @Nullable Account account(String iban) {
        var wanted = normalised(requireNonNull(iban, "iban"));
        for (var account : accounts()) {
            var own = account.iban();
            if (own != null && normalised(own).equals(wanted)) {
                return account;
            }
        }
        return null;
    }

    private static String normalised(String iban) {
        return iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private String onlyAccountId() {
        var id = onlyAccountId;
        if (id == null) {
            id = client.onlyAccount(token);
            onlyAccountId = id;
        }
        return id;
    }
}
