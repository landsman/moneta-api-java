# Roadmap

A working document: what is still unknown about MONETA API and what this library does next. How MONETA API
behaves, as far as it is known, is [moneta-api.md](../moneta-api.md).

## Current state (2026-11-07)

- **Released:** `0.0.1` on GitHub Packages, the client and its Spring Boot starter, as a pre-release
  ([install](../install.md)).
- **The client reads** accounts and transactions, pending and booked, with symbols, message, counterparty and
  `incoming()`, and tells a card payment from a transfer with `method()`. Checked against a live business account
  on 2026-11-06 and 2026-11-07, a card payment pending on 2026-11-07 and booked on 2026-11-10.
- **Tested** on Java 21 and 25, Spring Boot 3.5.12, 3.5.16, 4.0.0 and 4.1.2, a Kotlin caller, and Jackson 3.0.4.
- **The developer portal registration** (MONETA API – TPP) was approved. It gives the specification and a
  sandbox; production stays the merchant's token, as without a PSD2 licence the portal offers no more
  ([access](../access.md)).
- **[examples/cafe](../../examples/cafe/README.md)** issues bills as QR payments and finds them paid, live.

## Still unknown

Not in the specification `VipAISPPayments_v4` 1.0.1, read 2026-11-11:

- **Rate limits**: the specification documents a 429, but no limit and no `Retry-After`. The portal lists "usage
  quotas" in the organisation's settings, not looked at yet. Until known, a 429 fails the whole read. If a
  normal read can hit them, the client retries the page it stopped on, only after a short `Retry-After`, a few
  times, and that can be turned off.
- **A merchant token's rights and lifetime**: whether it can be read-only, how long it lives. A sandbox token
  lives 1200 seconds.
- **Reversals**: the specification calls `reversalIndicator: true` a cancellation; not seen live.
- **`business_name` with diacritics**: the client accepts ASCII only for now.
- **Webhooks**, as an alternative to polling.

## Next

- **Balances**: `…/accounts/{id}/balance`, as `Money`
  ([#13](https://github.com/landsman/moneta-api-java/issues/13)).
- **Maven Central**, once the portal documentation confirms the shapes: signing, a release process on
  JReleaser ([#14](https://github.com/landsman/moneta-api-java/issues/14)).
- **A cold review** of the starter, the modules layout and the booked-movement changes
  ([#15](https://github.com/landsman/moneta-api-java/issues/15)).
- **Micronaut**: whether it earns a module or a section in the docs
  ([#19](https://github.com/landsman/moneta-api-java/issues/19)).

Answered by the specification: no movement has a time of day
([#9](https://github.com/landsman/moneta-api-java/issues/9)); the sandbox is the same host with a client
credentials token.

## Not covered

- **Statements**: the specification is in hand. `…/{id}/statements?from=&to=` lists them;
  `…/{id}/statement/download` with `format=JSON&feature=JSON$STND` returns one with its movements, other formats
  a `dataDownloadURL` for `…/statement?file=`.
- **Payments and standing orders** (`VipPISPPaymentsBatch_v4`, `VipPISPStandingOrders_v4`): each creates an item
  in Internet Banka that a person signs or rejects, so a client returns "waiting for a signature", never "paid".
  A batch holds up to 200 payments and has a status endpoint. Their specifications are not downloaded yet.
- **Card transactions as their own endpoint**: none in `VipAISPPayments_v4`.
- **A sandbox mode**: a sandbox token works as any `AccessToken`; renewing it every 20 minutes is left to the
  caller until someone needs more.
