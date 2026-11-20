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
me.tokenExpiry();                       // when the token stops working: ask the merchant for a new one before it does
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
me.tokenExpiry()                       // when the token stops working: ask the merchant for a new one before it does
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
there are more. Name the account then. To read the one you stored, find it by its IBAN, which is what the
merchant gave you; MONETA's own account id is known to nobody outside it:

```java
var account = me.accountByIban(storedIban);   // only letters and digits count, in either case
if (account == null) {
    return refuse();                          // the token no longer sees it: revoked, or another token pasted
}
me.transactions(account.id()).between(from, to).list();

for (var other : me.accounts()) {             // or every account the token opens
    me.transactions(other.id()).between(from, to).cards().list();
}
```

<details><summary>Kotlin</summary>

```kotlin
val account = me.accountByIban(storedIban) ?: return refuse()
me.transactions(account.id).between(from, to).list()

for (other in me.accounts()) {
    me.transactions(other.id).between(from, to).cards().list()
}
```

</details>

`accountByIban` reads the accounts on every call, so an account revoked for the token shows on the next one. A
blank IBAN, or one failing its check digits, is an `IllegalArgumentException`: a typo, not a revoked account.
Two accounts with one IBAN, one per currency, are an `IllegalStateException` naming both ids.

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

Each message names the request and, when MONETA sent one, its `X-Request-Id`, which MONETA's support asks for
about one call: `GET /api/v4/vip/aisp/my/accounts?size=100&page=0 (X-Request-Id 7f3c…) answered 503`. Never the
token, nor the body; `body()` on `InvalidToken` and `HttpError` has the start of it.

## Logging

The client writes one DEBUG line per HTTP request, as JSON, to the logger `cz.insuit.moneta.MonetaClient`
through `System.Logger`: a read of several pages makes several, and an interrupted request none. Never the token,
never the body. Nothing above DEBUG: a failure is thrown, and the application logs it.

```json
{"request":"GET /api/v4/vip/aisp/my/accounts?size=100&page=0","status":200,"millis":143,"requestId":"7f3c…","error":null}
```

`error` is set when no answer came, and `status` is then `null`. To turn it on:

| Stack | Set |
|-------|-----|
| Spring Boot | `logging.level.cz.insuit.moneta=DEBUG` |
| Quarkus | `quarkus.log.category."cz.insuit.moneta".level=DEBUG` |
| Play | `<logger name="cz.insuit.moneta" level="DEBUG"/>` in `logback.xml` |
| Micronaut | `logger.levels.cz.insuit.moneta: DEBUG`, plus `slf4j-jdk-platform-logging` ([why](../modules/moneta-api-micronaut/README.md#logging-and-failures)) |
| SLF4J without a framework | `slf4j-jdk-platform-logging` on the classpath, then the logger at DEBUG in your config |
| nothing but the JDK | the `java.util.logging` file below, with `-Djava.util.logging.config.file=logging.properties` |

```properties
handlers = java.util.logging.ConsoleHandler
java.util.logging.ConsoleHandler.level = FINE
cz.insuit.moneta.level = FINE
```

## Settings

`MonetaClient.builder()`: `baseUri(…)`, `requestTimeout(…)`, `partner(…)` (headers MONETA does not ask for yet),
`configureHttpClient(…)` for a proxy or mTLS, `decorateHttpClient(…)` for tracing or metrics
([examples](compatibility.md#your-own-http-stack)). `close()` releases the HTTP client it made (from Java 21; on 17
it is released once nothing references it).
