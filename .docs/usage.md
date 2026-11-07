# Using the client

```java
var moneta = new MonetaClient(); // production, nothing to set

for (var account : moneta.accounts(token)) {
    for (var t :
            moneta.transactions(token, account.id(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))) {
        if (t.booked()) {
            System.out.println(t.amount() + " VS " + t.symbols().variable());
        }
    }
}
```

- **One client for many merchants.** The token goes with each call as an `AccessToken` and is never stored,
  logged or put in an exception message. Its own type keeps it from being swapped with the account id, and it
  refuses a value with a space or a control character, without repeating it.
- **A movement's `entryReference` changes when it is booked.** Pending and booked, the same payment carries two
  different ids, so deduplicating by it counts the payment twice. Act on booked movements only, or match the
  booked one to the pending one by symbol, amount and counterparty account, as `examples/cafe` does.
- **Once booked a movement carries `message()`** (what the payer wrote) **and `counterpartyName()`**; pending, the
  message is "Okamžitá platba" on an instant payment and the name is missing. `counterpartyAccount()` is there
  in both. All three stay out of `toString()`.
- **Which date is the payment's**: `enteredDate()` while pending, `valueDate()` once booked. `bookingDate()` can
  be a later business day, and `enteredDate()` is null on a booked credit. [How MONETA API behaves](moneta-api.md)
  has the whole table.
- **No time of day**: a movement has dates only. To tell "paid in the last minute", store when your poller
  first saw each `entryReference`; polling every few seconds makes that as close as it gets.
  `examples/cafe` keeps it as `first_seen_at`.
- **`balance(token, accountId)`** gives the booked and the available balance, signed, and `at()`, the instant
  MONETA computed it: unlike a movement, a balance has a time of day.
- **`reversed()`** marks a movement MONETA flags as a reversal; a shop should not count it as a payment.
- **`incoming(…)`** is `transactions(…)` with only the money that came in, the call a shop checking payments
  makes. Debits are dropped by the client; MONETA has no such filter that is known.
- **Amounts are `Money`**, a `BigDecimal` with a `Currency`, parsed without a detour through `double`. MONETA
  sends them positive beside a direction; `amount()` is signed, negative for money out, so amounts add up as
  they are. `1500` and `1500.00` CZK are equal.
- **The status is an enum**, `BOOKED`, `PENDING` or `OTHER` for a code not known yet. An unknown direction is
  refused instead: a wrong sign on money is worse than a failed read.
- **Payment symbols** come in one string, `"VS:123456 KS:0308 SS:0001"` or comma-separated. `PaymentSymbols`
  reads either and keeps leading zeros.
- **`raw`** holds each transaction as JSON, for the fields not mapped yet. It, the message and the counterparty
  are left out of `toString()`, so logging a transaction does not log who paid. A JSON serialiser still writes it,
  name and IBAN of the counterparty included, so map a `Transaction` to your own type before returning it from
  a controller or logging it as JSON.
- **A read is all or nothing.** A 429 on page five discards pages one to four, and a retry starts over. Read a
  long range month by month.
- **One client per application.** It is `AutoCloseable`; `close()` releases the HTTP client it made, not one
  you passed in.
- **Your own HTTP stack**: `configureHttpClient(…)` for a proxy or mTLS, `decorateHttpClient(…)` for tracing or
  metrics, both keeping the client's own settings and its `close()`. [Compatibility](compatibility.md#your-own-http-stack)
  has the examples and the traps.
- **Failures** are a sealed `MonetaException`, one subtype per thing to do about it, so a `switch` over them is
  exhaustive: `InvalidToken` (401, 403) needs a new token, `RateLimited` (429) carries `retryAfter()`,
  `HttpError` any other status, `NoAnswer` a timeout or a refused connection, `InvalidResponse` a body that is
  not the expected shape. `InvalidToken` and `HttpError` keep the start of MONETA's answer in `body()`, out of
  the message. The constructors are public, so a fake in your tests can throw them.
- **An interrupted thread is cancelled, not failed:** a `CancellationException` with the interrupt flag kept,
  outside `MonetaException`, so a retry on `NoAnswer` does not repeat work that was cancelled on purpose, and a
  Kotlin coroutine reads it as cancellation.
- **Records may gain components in a minor version** before 1.0. Read them by accessor; a record pattern over
  `Transaction` or `Account` would break on the next field.

---

## Optional settings

`MonetaClient.builder()` sets what differs from production: `baseUri(…)` for the sandbox, `requestTimeout(…)`, and
`partner(…)`, the three headers that name a registered application. MONETA does not ask for the partner headers:
every call so far went through without ([how it behaves](moneta-api.md)). Likely for an application approved on the
developer portal; set them once the portal says so.
