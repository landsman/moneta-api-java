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
var moneta = new MonetaClient();                  // or new MonetaClient(sandboxUri, partner)

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
- **Amounts are `BigDecimal`**, parsed without a detour through `double`. MONETA sends them positive with a
  direction; `signedAmount()` applies it.
- **Payment symbols** come in one string, `"VS:123456 KS:0308 SS:0001"` or comma-separated. `PaymentSymbols`
  reads either and keeps leading zeros.
- **`raw`** holds each transaction as MONETA sent it, for the fields not mapped yet (the counterparty among them).
- **Failures:** `MonetaException.InvalidToken` (401, 403) means a new token is needed, `RateLimited` (429)
  means wait, and any other `MonetaException` carries its status, or 0 when no answer came.

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
