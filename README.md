# moneta-api-java

A Java client for **MONETA API**, MONETA Money Bank's API for business accounts. A merchant opens it to a
third-party application with a token they issue in Internet Banka, so the application needs no PSD2 licence.
Usable from Java 21 and from Kotlin; its only dependencies are Jackson and JSpecify.

Partly verified against the live API, not on Maven Central yet: see [what is known](.docs/moneta-api.md).

| | |
|---|---|
| [Using the client](.docs/usage.md) | accounts, transactions, money, symbols, failures |
| [Spring Boot starter](modules/moneta-api-spring-boot-starter/README.md) | `moneta.*` in application.yml, a `MonetaClient` bean |
| [Kotlin and Spring Boot](.docs/compatibility.md) | what a Kotlin caller gets, and the one fix Spring Boot 3 before 3.5.13 needs |
| [What is known about MONETA API](.docs/moneta-api.md) | what the live API showed, what is still open, what is not covered |
| [Development](.docs/development.md) | build, tests, `make dev` against a real account |

Licence: [Apache 2.0](LICENSE)
