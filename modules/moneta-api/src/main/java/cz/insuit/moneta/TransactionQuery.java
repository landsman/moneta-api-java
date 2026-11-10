package cz.insuit.moneta;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The movements on one account, narrowed step by step and read by {@link #list()}:
 *
 * <pre>{@code
 * moneta.with(token).transactions().between(from, to).transfers().outgoing().list();
 * }</pre>
 *
 * Only {@link #between} is required. Each step returns a new query, so a base one can be narrowed two ways; a
 * later step of the same kind replaces the earlier one ({@code incoming().outgoing()} is outgoing). MONETA filters
 * by date only, so {@link #list()} reads every movement in the range and drops the rest here.
 */
public final class TransactionQuery {

    private final MonetaClient client;
    private final AccessToken token;
    private final Supplier<String> accountId;
    private final @Nullable LocalDate from;
    private final @Nullable LocalDate to;
    private final Transaction.@Nullable Method method;
    private final Transaction.@Nullable Direction direction;
    private final Transaction.@Nullable Status status;

    TransactionQuery(MonetaClient client, AccessToken token, Supplier<String> accountId) {
        this(client, token, accountId, null, null, null, null, null);
    }

    /** Both days inclusive, as {@link MonetaClient#transactions(AccessToken, String, LocalDate, LocalDate)} reads. */
    public TransactionQuery between(LocalDate from, LocalDate to) {
        return new TransactionQuery(
                client, token, accountId, requireNonNull(from), requireNonNull(to), method, direction, status);
    }

    /** Only domestic payment orders, instant ones included. */
    public TransactionQuery transfers() {
        return method(Transaction.Method.TRANSFER);
    }

    /** Only card payments, pending holds included unless {@link #booked()} follows. */
    public TransactionQuery cards() {
        return method(Transaction.Method.CARD);
    }

    /** Only movements made this way. */
    public TransactionQuery method(Transaction.Method method) {
        return new TransactionQuery(client, token, accountId, from, to, requireNonNull(method), direction, status);
    }

    /** Only money that came in. */
    public TransactionQuery incoming() {
        return new TransactionQuery(client, token, accountId, from, to, method, Transaction.Direction.CREDIT, status);
    }

    /** Only money that went out. */
    public TransactionQuery outgoing() {
        return new TransactionQuery(client, token, accountId, from, to, method, Transaction.Direction.DEBIT, status);
    }

    /** Only booked movements, which will not change. */
    public TransactionQuery booked() {
        return new TransactionQuery(client, token, accountId, from, to, method, direction, Transaction.Status.BOOKED);
    }

    /** Only pending movements. */
    public TransactionQuery pending() {
        return new TransactionQuery(client, token, accountId, from, to, method, direction, Transaction.Status.PENDING);
    }

    /**
     * Reads the range, all pages, and keeps what every step asked for.
     *
     * @throws IllegalStateException when {@link #between} was not called, or the query names no account and the
     *     token opens other than exactly one
     * @throws IllegalArgumentException when {@code to} is before {@code from}, or the account id is blank or a
     *     dot segment
     * @throws MonetaException when MONETA refuses, fails or answers in an unexpected shape; see its subtypes
     * @throws CancellationException when the thread is interrupted; its interrupt flag stays set
     */
    public List<Transaction> list() {
        if (from == null || to == null) {
            throw new IllegalStateException("call between(from, to) first: MONETA reads a date range");
        }
        return client.transactions(token, accountId.get(), from, to).stream()
                .filter(t -> method == null || t.method() == method)
                .filter(t -> direction == null || t.direction() == direction)
                .filter(t -> status == null || t.status() == status)
                .toList();
    }

    private TransactionQuery(
            MonetaClient client,
            AccessToken token,
            Supplier<String> accountId,
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            Transaction.@Nullable Method method,
            Transaction.@Nullable Direction direction,
            Transaction.@Nullable Status status) {
        this.client = requireNonNull(client);
        this.token = requireNonNull(token);
        this.accountId = requireNonNull(accountId);
        this.from = from;
        this.to = to;
        this.method = method;
        this.direction = direction;
        this.status = status;
    }
}
