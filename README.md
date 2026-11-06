# moneta-api-java

A Java client for **MONETA API**, MONETA Money Bank's API for business accounts. A merchant opens it to a
third-party application with a token they issue in Internet Banka, so the application needs no PSD2 licence.
Usable from Java 21 and from Kotlin; its only dependencies are Jackson and JSpecify.

> **Partly verified.** MONETA publishes the specification only to organisations registered in its
> [developer portal](https://apiportal.moneta.cz/pages/moneta/home.html). The paths, headers and response
> shapes started from the one public client, [sloukapetr/moneta-laravel-package](https://github.com/sloukapetr/moneta-laravel-package);
> on 2026-11-06 they were checked against a live business account with pending incoming and outgoing
> payments, and held, except the pending dates now handled. Booked movements are not yet checked. Not on Maven
> Central until they are.

## Use

```java
var moneta = new MonetaClient(); // or MonetaClient.builder().baseUri(sandbox).partner(partner).build()

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
- **Amounts are `Money`**, a `BigDecimal` with a `Currency`, parsed without a detour through `double`. MONETA
  sends them positive beside a direction; `amount()` is signed, negative for money out, so amounts add up as
  they are. `1500` and `1500.00` CZK are equal.
- **The status is an enum**, `BOOKED`, `PENDING` or `OTHER` for a code not known yet. An unknown direction is
  refused instead: a wrong sign on money is worse than a failed read.
- **Payment symbols** come in one string, `"VS:123456 KS:0308 SS:0001"` or comma-separated. `PaymentSymbols`
  reads either and keeps leading zeros.
- **`raw`** holds each transaction as JSON, for the fields not mapped yet (the counterparty among them). It is
  left out of `toString()`, so logging a transaction does not log who paid. A JSON serialiser still writes it,
  name and IBAN of the counterparty included, so map a `Transaction` to your own type before returning it from
  a controller or logging it as JSON.
- **A read is all or nothing.** A 429 on page five discards pages one to four, and a retry starts over. Read a
  long range month by month.
- **One client per application.** It is `AutoCloseable`; `close()` releases the HTTP client it made, not one
  you passed in.
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

## Kotlin

The JSpecify annotations make `iban` a `String?` and a `when` over `MonetaException` exhaustive without an
`else`, enforced from Kotlin 2.1. Kotlin 1.9, which Spring Boot 3 manages, only warns; add
`-Xjspecify-annotations=strict` to the compiler options to make it an error.

## Spring Boot 3

The library uses Jackson 3, which lives beside a Spring Boot 3 application's Jackson 2 without conflict, except
for one jar the two share: `com.fasterxml.jackson.core:jackson-annotations`. Jackson 3.2 needs 2.21 or newer,
and Spring Boot's BOM pins it lower up to 3.5.12, so the client fails to load with `NoClassDefFoundError`. On
those versions raise it in your own dependency management; Jackson 2.18 databind runs on it.

```xml
<!-- Maven, in <dependencyManagement><dependencies> -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
    <version>2.21</version>
</dependency>
```

```kotlin
// Gradle with io.spring.dependency-management
dependencyManagement { dependencies { dependency("com.fasterxml.jackson.core:jackson-annotations:2.21") } }
```

Spring Boot 3.5.13 and newer, and every Spring Boot 4, need nothing. `make build` checks all three cases, and a
Kotlin caller, in [testing/](testing/README.md).

## Not covered yet

Payments (PISP), balances, statements, OAuth client credentials for the sandbox. Each waits for the
specification.

GraalVM native image is untested. The JSON records are bound by reflection and the jar carries no
reachability metadata, so in a native image every call would likely fail as `InvalidResponse`. Supporting it
means shipping `META-INF/native-image/…/reachability-metadata.json` and running the tests once natively.

## What the live API showed (2026-11-06)

- `fromDate`, `toDate`, `page` and `size` are the real names: `size=100` comes back as `pageSize: 100`, a
  reversed range is a 400 `DT01`. An unknown name is ignored without an error, so a typo returns the account
  unfiltered.
- Pages count from 0. A page past the last is a 404 `PAGE_NOT_FOUND`, not an empty page; the client stops by
  `pageCount`, which MONETA always sends.
- An empty range is `"transactions": []`.
- The partner headers are not required: every call above was made without them.
- A pending movement (`PDNG`) has `bookingDate` and `valueDate` as `{"date": null}`; only `enteredDate` is set.
- A debit comes as `DBIT` with a positive value, as assumed.
- The symbols are one string: `"VS:1337 KS:1000 SS:76789"`, `"VS:1337  SS:76789"` with KS left out,
  `"VS:"` with none.
- No counterparty name is sent, only its account number. The message for the recipient is "Okamžitá platba"
  on instant payments, in Internet Banka's own export as well.
- An account has no `nameI18N` unless one is set, so `Account.name()` is null; `productI18N` names the
  product.
- A nonsense date is a 500, not a 400.

## What is still open

- Booked movements: when a pending one becomes `BOOK`, its dates, and whether a message appears.
- Whether `toDate` is inclusive, and which date the range filters on (booking, value or entered).
- How `business_name` is sent with diacritics; the client accepts only ASCII there for now.
- Whether a token can be read-only, and how long it lives.
- The rate limits: per token, per application or per partner, over what window, and whether a 429 carries
  `Retry-After`. Until they are known a 429 fails the whole read. If a normal read can hit them, the client
  will retry the page it stopped on, only after a short `Retry-After` and a few times, and that can be turned
  off; a limit shared across merchants still needs a limiter on the caller's side.

## Build

`make build` compiles, tests and packages; `make` lists the targets. A JDK 21 or newer is enough, and
`mise.toml` pins the one used here.

## Licence

[Apache 2.0](LICENSE)
