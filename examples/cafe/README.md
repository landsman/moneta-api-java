# Café payments

An example app: a café that issues bills as QR payments and finds them paid among the payments reaching its
MONETA account, the way an application uses the library. Spring Boot 4.1 with the starter, no configuration
class for MONETA.

    MONETA_TOKEN=op://<vault>/<item>/<field> op run -- make dev

Then try it by hand at http://localhost:8080/scalar, an API reference built from the controllers.

| Request | Answers |
|---------|---------|
| `POST /bills` `{"amount": 65.00}` | a new bill for 65.00 CZK, answered with its QR code as a PNG; its variable symbol is in `X-Variable-Symbol` |
| `GET /bills` | every bill, newest first: paid or not, by which payment, seen when |
| `GET /bills/{variableSymbol}` | whether that bill is paid; still unpaid, it is first looked for in MONETA |
| `GET /payments` | today's incoming payments |
| `GET /payments?period=yesterday` | `today`, `yesterday`, `this-week`, `last-week`, `last-7-days`, `this-month` |
| `GET /payments?from=2026-11-01&to=2026-11-06` | a range of its own, both days included; wins over `period` |

## How a bill is found paid

- **A bill's variable symbol is its id**, ascending from 1000 and never reused, so no two bills share one. The
  bills live in H2 in `build/cafe`; `./gradlew clean` starts afresh.
- **Every `cafe.check-moneta-every` (30 s)** the café reads the incoming payments since its oldest unpaid bill
  was issued, pending ones included, and a payment pays a bill when the variable symbol, amount and currency are the
  bill's exactly (leading zeros in the symbol do not count). `PaymentMatching` is that rule, tested on its own.
- **One payment pays one bill**: the table holds a unique `moneta_entry_reference`, MONETA's id of the payment, so the database refuses a second.
- **`paidSeenAt` is when the café first saw the payment.** MONETA gives a payment a date and no time, so this,
  to the polling interval, is the closest there is to when it was paid.
- **The QR code** is drawn by [spayd-qr-java](https://github.com/janhalasa/spayd-qr-java), with instant payment
  asked for (`PT:IP`); a symbol over 10 digits or an amount over 9 999 999.99 is refused rather than cut by
  the reader.

Each payment in `/payments` carries its reference, amount, currency, variable symbol, whether it is booked, and
its day: the booking day, or while pending the day MONETA took it in. The counterparty's account stays out.

**`/payments` covers whole days only.** MONETA dates a movement but gives it no time, so "the last hour" cannot
be answered from its data; the bills show the way round it, recording when each payment was first seen.

## Configuration

`src/main/resources/application.yml`. `moneta.*` is the
[starter's](../../modules/moneta-api-spring-boot-starter/README.md), which
builds the `MonetaClient` and, since a café reads its one account, the `AccessToken`; `cafe.*` is `CafeProperties`:

| Property | |
|----------|---|
| `moneta.token` | `${MONETA_TOKEN}`; set it in the environment, never in a file |
| `cafe.time-zone` | whose "today" a period means, `Europe/Prague` |
| `cafe.check-moneta-every` | how often unpaid bills are looked for, `PT30S` |
| `cafe.iban` | the café's account, which the QR payments pay into; required |

The rest of `moneta.*` keeps the starter's defaults, production among them.

A blank token or one with a stray newline stops the app at startup. Use a read-only token, and revoke it in
Internet Banka when done.
