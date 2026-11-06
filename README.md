# moneta-api-java

A Java client for **MONETA API**, MONETA Money Bank's API for business accounts. A merchant opens it to a
third-party application with a token they issue in Internet Banka, so the application needs no PSD2 licence.
Usable from Java 21 and from Kotlin; its only dependencies are Jackson and JSpecify.

> **Unverified.** MONETA publishes the specification only to organisations registered in its
> [developer portal](https://apiportal.moneta.cz/pages/moneta/home.html). The paths, headers and response
> shapes here follow the one public client, [sloukapetr/moneta-laravel-package](https://github.com/sloukapetr/moneta-laravel-package),
> whose test data is its author's own. Not on Maven Central until it has been run against the sandbox.

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

Spring Boot 3.5.13 and newer, and every Spring Boot 4, need nothing. The tests also run on Jackson 3.0.4, the
oldest Spring Boot 4 ships (`make build` does both).

## Not covered yet

Payments (PISP), balances, statements, OAuth client credentials for the sandbox. Each waits for the
specification.

## What the specification has to answer

- The transaction filter's parameter names; `fromDate`, `toDate`, `page` and `size` are the Czech open-banking
  standard's, assumed here.
- Whether `pageNumber` starts at 0. The client refuses a page numbered other than it asked for, rather than
  read it twice.
- Whether `toDate` is inclusive, which date the range filters on (booking or value), and whether it returns
  pending movements.
- The format of `bookingDate` and `valueDate`, and whether `reference` is one string or a list.
- How `business_name` is sent with diacritics; the client accepts only ASCII there for now.
- Whether the `identification_number`, `business_name` and `application_name` headers are required, and whose
  values they hold.
- Whether a token can be read-only, how long it lives, and the rate limits.

## Build

`make build` compiles, tests and packages; `make` lists the targets. A JDK 21 or newer is enough, and
`mise.toml` pins the one used here.

## Licence

[Apache 2.0](LICENSE)
