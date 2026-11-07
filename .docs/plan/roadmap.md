# Roadmap

A working document: what is still unknown about MONETA API and what this library does next. How MONETA API
behaves, as far as it is known, is [moneta-api.md](../moneta-api.md).

## Current state (2026-11-07)

- **Released:** `0.0.1` on GitHub Packages, the client and its Spring Boot starter, as a pre-release
  ([install](../install.md)).
- **The client reads** accounts and transactions, pending and booked, with symbols, message, counterparty and
  `incoming()`. Checked against a live business account on 2026-11-06 and 2026-11-07.
- **Tested** on Java 21 and 25, Spring Boot 3.5.12, 3.5.16, 4.0.0 and 4.1.2, a Kotlin caller, and Jackson 3.0.4.
- **The developer portal registration** (MONETA API – TPP) waits for MONETA's manual approval; until then
  there is no sandbox and no documentation, only the production API with a merchant's token.
- **[examples/cafe](../../examples/cafe/README.md)** issues bills as QR payments and finds them paid, live.

## Still unknown

Mostly behind the portal, so expected once the registration is approved
([#11](https://github.com/landsman/moneta-api-java/issues/11)):

- **A time of day for a movement**, if any field or endpoint has one
  ([#9](https://github.com/landsman/moneta-api-java/issues/9)).
- **Rate limits**: per token, application or partner; the window; whether a 429 carries `Retry-After`. Until
  known, a 429 fails the whole read. If a normal read can hit them, the client retries the page it stopped on,
  only after a short `Retry-After`, a few times, and that can be turned off.
- **Token rights and lifetime**: whether a token can be read-only, how long it lives.
- **The sandbox's host** and how its tokens are issued.
- **Reversals**: what a movement with `reversalIndicator: true` looks like.
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

## Not covered

Payments (PISP), statements, standing orders, card transactions as their own endpoint, OAuth client credentials
for the sandbox. Each waits for the specification.

**GraalVM native image** is untested. The JSON records are bound by reflection and the jar carries no
reachability metadata, so in a native image every call would likely fail as `InvalidResponse`. Supporting it
means shipping `META-INF/native-image/…/reachability-metadata.json` and running the tests once natively.
