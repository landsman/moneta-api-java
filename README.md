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
var moneta = new MonetaClient();   // or MonetaClient.builder().baseUri(sandbox).partner(partner).build()

for (var account : moneta.accounts(token)) {
    for (var t : moneta.transactions(token, account.id(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))) {
        if (t.booked()) {
            System.out.println(t.signedAmount() + " " + t.currency() + " VS " + t.symbols().variable());
        }
    }
}
```

- **One client for many merchants.** The token goes with each call and is never stored, logged or put in an
  exception message.
- **Amounts are `Money`**, a `BigDecimal` with a `Currency`, parsed without a detour through `double`. MONETA
  sends them positive with a direction; `signedAmount()` applies it.
- **The status is an enum**, `BOOKED`, `PENDING` or `OTHER` for a code not known yet. An unknown direction is
  refused instead: a wrong sign on money is worse than a failed read.
- **Payment symbols** come in one string, `"VS:123456 KS:0308 SS:0001"` or comma-separated. `PaymentSymbols`
  reads either and keeps leading zeros.
- **`raw`** holds each transaction as JSON, for the fields not mapped yet (the counterparty among them). It is
  left out of `toString()`, so logging a transaction does not log who paid.
- **Failures** are a sealed `MonetaException`, one subtype per thing to do about it, so a `switch` over them is
  exhaustive: `InvalidToken` (401, 403) needs a new token, `RateLimited` (429) carries `retryAfter()`,
  `HttpError` any other status, `NoAnswer` a timeout or a refused connection, `InvalidResponse` a body that is
  not the expected shape. A token with a space or a control character is refused before it is sent, without
  being repeated in the message.
- **Records may gain components in a minor version** before 1.0. Read them by accessor; a record pattern over
  `Transaction` or `Account` would break on the next field.

## Not covered yet

Payments (PISP), balances, statements, OAuth client credentials for the sandbox. Each waits for the
specification.

## What the specification has to answer

- The transaction filter's parameter names; `fromDate`, `toDate`, `page` and `size` are the Czech open-banking
  standard's, assumed here.
- Whether `pageNumber` starts at 0.
- Whether the `identification_number`, `business_name` and `application_name` headers are required, and whose
  values they hold.
- Whether a token can be read-only, how long it lives, and the rate limits.

## Build

`make build` compiles, tests and packages; `make` lists the targets. A JDK 21 or newer is enough, and
`mise.toml` pins the one used here.

## Licence

[Apache 2.0](LICENSE)
