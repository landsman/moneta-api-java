# Café payments

An example app: a café that issues bills as QR payments and finds them paid among the payments reaching its
MONETA account, the way an application uses the library. Spring Boot 4.1 with the starter, no configuration
class for MONETA.

    MONETA_TOKEN=op://<vault>/<item>/<field> op run -- make dev

Then try it by hand at http://localhost:8080/scalar, an API reference built from the controllers.

| Request | Answers |
|---------|---------|
| `POST /bills` `{"amount": 65.00}` | a new bill for 65.00 CZK, answered with its QR code as a PNG; its variable symbol is in `X-Variable-Symbol` |
| `GET /bills` | every bill, newest first, with its payments, what they add up to and its status |
| `GET /bills/{variableSymbol}` | one bill the same way; not covered yet, the payments are read from MONETA first |
| `GET /payments/recorded` | every payment the café recorded, newest first |
| `GET /payments/recorded?unassigned=true` | the payments no bill claimed: a mistyped symbol, another day's, none |
| `PUT /payments/recorded/{id}/bill` `{"variableSymbol": "1000"}` | links that payment to a bill by hand; `null` unlinks it |
| `GET /balance` | the café account's booked and available balance, and when MONETA computed it |
| `GET /payments` | today's incoming payments, straight from MONETA |
| `GET /payments?period=yesterday` | `today`, `yesterday`, `this-week`, `last-week`, `last-7-days`, `this-month` |
| `GET /payments?from=2026-11-01&to=2026-11-06` | a range of its own, both days included; wins over `period` |

## How a bill is found paid

Two tables in H2 (`build/cafe`, kept across restarts; `./gradlew clean` starts afresh): `bill` and `payment`.

- **A bill's variable symbol is its id**, ascending from 1000 and never reused, so no two bills share one. A
  bill holds only its amount and when it was issued.
- **Every payment into the café's account is a row in `payment`**, recorded every `cafe.check-moneta-every`
  (30 s) by `PaymentRecorder`, pending ones included. A payment whose variable symbol names a bill is linked to
  it; any other stays unlinked, for a person to link with `PUT /payments/recorded/{id}/bill`. A mistyped symbol
  or a payment for another day's bill is that person's anyway.
- **A bill's status is what its payments add up to**: `UNPAID`, `PARTLY_PAID`, `PAID` or `OVERPAID`. Paid twice
  shows as overpaid, two halves as paid; nothing is dropped.
- **One payment is one row, pending or booked.** MONETA gives a payment a new id once booked, so a booked payment
  that matches a pending row (same amount, symbol and payer's account) books that row instead of adding
  one. The payer's account is compared as one value however MONETA wrote it: pending `4040404040/0800`, booked
  `0 4040404040/0800`. `PaymentRecording` is that rule, tested on its own.
- **The read covers yesterday and today**, so a payment just before midnight is not missed, and reaches back to
  the day a still pending payment was made, since booking came three days later over a weekend; never more than
  14 days back.
- **A payment is written once, whatever races it.** The schedule and a `GET /bills/{variableSymbol}` can read
  at the same moment. Recording is `insert … on conflict do nothing` and booking an `update … where booked =
  false`, each one statement whose row count says whether this round won (`queries/`, run by `PaymentWrites`);
  never a lookup first, which two rounds would both pass. H2 runs in PostgreSQL mode for it. A test races eight
  threads on one payment and expects one row.
- **Two payers on one symbol stay two rows**, each with the payer's account, and once booked their name.
- **A reversal is not recorded.** The payer's message is kept only once booked: pending, MONETA sends
  "Okamžitá platba" there.
- **`first_seen_at` is when the café first saw a payment.** MONETA gives a payment a date and no time, so this,
  to the polling interval, is the closest there is to when it was paid.
- **The QR code** is drawn by [spayd-qr-java](https://github.com/janhalasa/spayd-qr-java), with instant payment
  asked for (`PT:IP`); a symbol over 10 digits or an amount over 9 999 999.99 is refused rather than cut by
  the reader.

**A database from an earlier version** is upgraded in place, but its bill columns about payments are dropped
without being carried over: a bill paid before yesterday comes back unpaid, since the read only reaches yesterday.
`./gradlew clean` is the honest start.

**No sign-in.** `/bills` and `/payments/recorded` answer anyone who can reach the port, with each payer's account,
name and message. That is an example's shortcut; an app behind a till puts its own sign-in in front, and keeps
these out of any log the way the library keeps them out of `toString()`.

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
