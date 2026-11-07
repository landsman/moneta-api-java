# moneta-api-java

The Czech bank MONETA Money Bank lets its business clients, sole traders included, use
[its API](https://www.moneta.cz/zivnostnici-a-firmy/api) for free: an application reads their accounts and
transactions, and can enter payments, with a token they issue in Internet Banka. No PSD2 licence is needed.

This is a client for that API, **MONETA API**, for Java 21+ and Kotlin projects, with a Spring Boot starter, a
Play Framework module and a Micronaut module. It reads accounts and transactions so far; payments are not covered
yet. Its only dependencies are Jackson and JSpecify.

Checked against the live API, see [how it behaves](.docs/moneta-api.md). Published on GitHub Packages, not
Maven Central.

| Read | For |
|---|---|
| [Installing](.docs/install.md) | the four artifacts on GitHub Packages, for Gradle and Maven |
| [Getting access](.docs/access.md) | who can use MONETA API, the merchant's token, the developer portal |
| [Using the client](.docs/usage.md) | accounts, transactions, money, symbols, failures |
| [Spring Boot starter](modules/moneta-api-spring-boot-starter/README.md) | `moneta.*` in application.yml, a `MonetaClient` bean |
| [Play Framework module](modules/moneta-api-play/README.md) | `moneta.*` in application.conf, an injected `MonetaClient` |
| [Micronaut module](modules/moneta-api-micronaut/README.md) | the same `moneta.*`, a `MonetaClient` bean |
| [Kotlin and Spring Boot](.docs/compatibility.md) | what a Kotlin caller gets, the one fix Spring Boot 3 before 3.5.13 needs, and native images |
| [How MONETA API behaves](.docs/moneta-api.md) | what to watch out for: pending and booked movements, dates, ids, errors |
| [Roadmap](.docs/plan/roadmap.md) | the working plan: what is still unknown, what comes next, what is not covered |
| [Development](.docs/development.md) | build, tests, `make dev` against a real account |
| [Example café](examples/cafe/README.md) | bills as QR payments, found paid; try it at http://localhost:8080/scalar after `make dev` |

## What it covers

What MONETA API offers, per [its portal](https://apiportal.moneta.cz/pages/moneta/home.html), against what this
client implements:

| Feature | MONETA API | This client |
|---|:---:|:---:|
| Accounts the token sees | ✅ | ✅ |
| Transactions, pending and booked | ✅ | ✅ |
| Card transactions | ✅ | ⬜ |
| Balances | ✅ | ✅ |
| Pending card transactions | ✅ | ⬜ |
| Electronic statements | ✅ | ⬜ |
| Overview of payment orders | ✅ | ⬜ |
| Overview of standing orders | ✅ | ⬜ |
| CZK payment orders, bulk included | ✅ | ⬜ |
| Spring Boot auto-configuration | — | ✅ |
| Play Framework module | — | ✅ |
| Micronaut module | — | ✅ |
| GraalVM native image | — | ✅ |

Licence: [Apache 2.0](LICENSE)
