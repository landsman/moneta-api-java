# moneta-api-java

The Czech bank MONETA Money Bank lets its business clients, sole traders included, use its API for free: an
application reads their accounts and transactions, and can enter payments, with a token they issue in Internet
Banka. No PSD2 licence is needed.

This is a client for that API, **MONETA API**, for Java 21+ and Kotlin projects, with a Spring Boot starter. It
reads accounts and transactions so far; payments are not covered yet. Its only dependencies are Jackson and
JSpecify.

Partly verified against the live API, not on Maven Central yet: see [what is known](.docs/moneta-api.md).

| Read | For |
|---|---|
| [Getting access](.docs/access.md) | who can use MONETA API, the merchant's token, the developer portal |
| [Using the client](.docs/usage.md) | accounts, transactions, money, symbols, failures |
| [Spring Boot starter](modules/moneta-api-spring-boot-starter/README.md) | `moneta.*` in application.yml, a `MonetaClient` bean |
| [Kotlin and Spring Boot](.docs/compatibility.md) | what a Kotlin caller gets, and the one fix Spring Boot 3 before 3.5.13 needs |
| [What is known about MONETA API](.docs/moneta-api.md) | what the live API showed, what is still open, what is not covered |
| [Development](.docs/development.md) | build, tests, `make dev` against a real account |

Licence: [Apache 2.0](LICENSE)
