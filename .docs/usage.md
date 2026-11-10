# Using the client

```java
var moneta = new MonetaClient();                  // one per application
var me = moneta.with(new AccessToken(myToken));   // cheap, keep it or make one per request

var november = me.transactions().between(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

november.list();                        // everything
november.incoming().booked().list();    // money that came in, booked
november.transfers().outgoing().list(); // transfers that went out
november.cards().list();                // paid by the account's card

me.balance();                           // booked and available, with the instant MONETA computed it
```

<details><summary>Kotlin</summary>

```kotlin
val moneta = MonetaClient()                 // one per application
val me = moneta.with(AccessToken(myToken))  // cheap, keep it or make one per request

val november = me.transactions().between(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))

november.list()                        // everything
november.incoming().booked().list()    // money that came in, booked
november.transfers().outgoing().list() // transfers that went out
november.cards().list()                // paid by the account's card

me.balance()                           // booked and available, with the instant MONETA computed it
```

</details>

With the Spring Boot starter, `moneta.token` gives you the `AccessToken` bean.

## Filtering

| Step | Keeps |
|------|-------|
| `between(from, to)` | days, both inclusive; **required** |
| `transfers()`, `cards()`, `method(…)` | how the money moved |
| `incoming()`, `outgoing()` | money in, money out |
| `booked()`, `pending()` | booked, pending |

- Steps are optional and combine; a later one of the same kind wins.
- A query is immutable, so one base query can branch, as `november` above.
- Nothing is read until `list()`. MONETA filters by date only, so a shorter range is what makes it cheaper.

## More than one account

`transactions()` and `balance()` read the only account the token opens, and throw `IllegalStateException` when
there are more. Name the account then:

```java
for (var account : me.accounts()) {
    me.transactions(account.id()).between(from, to).cards().list();
}
```

<details><summary>Kotlin</summary>

```kotlin
for (account in me.accounts()) {
    me.transactions(account.id).between(from, to).cards().list()
}
```

</details>

Several account holders, each with their own token, share one client:

```java
holders.forEach(holder -> moneta.with(holder.token()).transactions().between(from, to).list());
```

<details><summary>Kotlin</summary>

```kotlin
holders.forEach { moneta.with(it.token).transactions().between(from, to).list() }
```

</details>

## What to know

- **`entryReference` changes when a movement is booked**: deduplicate by it and a payment counts twice. Act on
  booked movements, or match by symbols, amount and counterparty account, as `examples/cafe` does.
- **Dates only, no time of day.** The payment's date is `enteredDate()` while pending, `valueDate()` once booked.
  Need "paid a minute ago"? Store when you first saw it.
- **`amount()` is signed**: negative is money out, so amounts add up. `Money` is a `BigDecimal` with a currency.
- **`method()`** is `TRANSFER`, `CARD` or `OTHER`, and gains constants as they are seen live
  ([#66](https://github.com/landsman/moneta-api-java/issues/66)): keep a `default` in a `switch`.
- **`symbols()`** reads `VS`, `KS` and `SS`, leading zeros kept.
- **`reversed()`** is a reversal, not a payment.
- **`raw()`** has the whole movement as JSON. It, `message()` and the counterparty stay out of `toString()`, not
  out of a JSON serialiser: map to your own type before returning one from a controller.
- **A read is all or nothing**, every page or a failure. Read long ranges month by month.
- **Records may gain components before 1.0**: use accessors, not record patterns.

[How MONETA API behaves](moneta-api.md) has the details behind each.

## Failures

A sealed `MonetaException`, so a `switch` over it is exhaustive:

| Subtype | When | Do |
|---------|------|----|
| `InvalidToken` | 401, 403 | get a new token |
| `RateLimited` | 429 | wait `retryAfter()` |
| `HttpError` | any other status | `body()` has MONETA's answer |
| `NoAnswer` | timeout, refused connection | retry |
| `InvalidResponse` | an unexpected body | report it |

An interrupted thread throws `CancellationException` instead, interrupt flag kept.

## Settings

`MonetaClient.builder()`: `baseUri(…)`, `requestTimeout(…)`, `partner(…)` (headers MONETA does not ask for yet),
`configureHttpClient(…)` for a proxy or mTLS, `decorateHttpClient(…)` for tracing or metrics
([examples](compatibility.md#your-own-http-stack)). `close()` releases the HTTP client it made.
