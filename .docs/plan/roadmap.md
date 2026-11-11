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

Not in MONETA's specifications, read 2026-11-11:

- **Rate limits**: the specification documents a 429, but no limit and no `Retry-After`. The portal lists "usage
  quotas" in the organisation's settings, not looked at yet. Until known, a 429 fails the whole read. If a
  normal read can hit them, the client retries the page it stopped on, only after a short `Retry-After`, a few
  times, and that can be turned off.
- **A merchant token's rights**: whether it can be read-only. Its expiry is not unknown any more, see Next.
- **Reversals**: the specification calls `reversalIndicator: true` a cancellation; not seen live.
- **`business_name` with diacritics**: the client accepts ASCII only for now.
- **Webhooks**, as an alternative to polling.

Answered by the specification: no movement has a time of day
([#9](https://github.com/landsman/moneta-api-java/issues/9)); the sandbox is the same host with a client
credentials token; a merchant token's expiry is readable per token.


## Next

- **A token's expiry**: `GET /api/v1/vip/token/introspect` answers `{"expiration": "2026-03-06T11:38:09+01:00"}`
  for the token sent, and a 403 for one that is not a MONETA API token (`VipTokenIntrospect_v1` 1.0.0). As
  `client.tokenExpiry(token)`, an app warns the merchant before the token stops working, not after a 401.
- **Maven Central**, once the portal documentation confirms the shapes: signing, a release process on
  JReleaser ([#14](https://github.com/landsman/moneta-api-java/issues/14)).
- **A cold review** of the starter, the modules layout and the booked-movement changes
  ([#15](https://github.com/landsman/moneta-api-java/issues/15)).
- **Micronaut**: whether it earns a module or a section in the docs
  ([#19](https://github.com/landsman/moneta-api-java/issues/19)).

## Not covered

- **Statements**: the specification is in hand. `…/{id}/statements?from=&to=` lists them;
  `…/{id}/statement/download` with `format=JSON&feature=JSON$STND` returns one with its movements, other formats
  a `dataDownloadURL` for `…/statement?file=`.
- **Payments** (`VipPISPPaymentsBatch_v4` 1.0.0): `POST …/pisp/my/payments/batch`, up to 200, returns a
  `batchId`; `…/batch/{batchId}/status` is `ACTC` (waiting for the client's signature in Internet Banka), `RJCT` or
  `ACSP` (signed). `…/batch/onebyone` returns an id per payment, read by
  `…/batch/onebyone/{transactionId}/status`. A client returns "waiting for a signature", never "paid". Batches
  are normal priority only, no instant payments. **Symbols are written `VS:1,KS:2,SS:3`, with commas**, though
  a movement reads them back separated by spaces.
- **Standing orders** (`VipPISPStandingOrders_v4` 1.0.0): `POST …/pisp/my/standingorders`, CZK domestic only,
  weekly to yearly, first execution up to 360 days ahead, message and name at most 18 characters; answered with
  a status (`FOR_SIGNING`, `ACSC`, `CANCELLED` among others) and signed in Internet Banka.
- **Card transactions as their own endpoint**: none in `VipAISPPayments_v4`.
- **A sandbox mode**: a sandbox token works as any `AccessToken`; renewing it every 20 minutes is left to the
  caller until someone needs more.
