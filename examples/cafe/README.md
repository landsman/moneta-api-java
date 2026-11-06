# Café payments

An example app: a café checking which payments reached its MONETA account, the way an application uses the
library. Spring Boot 4.1, one `MonetaClient` bean, one endpoint.

    MONETA_TOKEN=op://<vault>/<item>/<field> op run -- make dev

| Request | Answers |
|---------|---------|
| `GET /payments` | today's incoming payments |
| `GET /payments?period=yesterday` | `today`, `yesterday`, `this-week`, `last-week`, `last-7-days`, `this-month` |
| `GET /payments?from=2026-11-01&to=2026-11-06` | a range of its own, both days included; wins over `period` |

Each payment carries its reference, amount, currency, variable symbol, whether it is booked, and its day: the
booking day, or while pending the day MONETA took it in. The counterparty's account stays out.

**Whole days only.** MONETA dates a movement but gives it no time, so "the last hour" cannot be answered from
its data. An app that needs it records when it first saw each `reference`, polling every few seconds.

## Configuration

`src/main/resources/application.yml`, bound to `MonetaProperties` and `CafeProperties`:

| Property | Default | |
|----------|---------|---|
| `moneta.token` | — | `${MONETA_TOKEN}`; set it in the environment, never in a file |
| `moneta.base-uri` | `https://api.moneta.cz/` | the sandbox's host once the portal gives it |
| `moneta.request-timeout` | `30s` | one call to MONETA |
| `cafe.time-zone` | `Europe/Prague` | whose "today" a period means |

`MonetaConfiguration` builds the one `MonetaClient` and the `AccessToken` from them; a blank token or one with a
stray newline stops the app at startup. Use a read-only token, and revoke it in Internet Banka when done.
