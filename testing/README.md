# Testing

Projects that use the library the way a caller's build would, so a version a caller is likely to have cannot
break it unnoticed. `make build` runs them with the library's own tests, which stay in `src/test`.

| Project | Proves |
|---------|--------|
| `spring-boot/3.5.12` | the `jackson-annotations` 2.21 fix from [compatibility](../.docs/compatibility.md) works on the last Spring Boot that needs it |
| `spring-boot/3.5.16` | Spring Boot 3.5.13 and newer need nothing |
| `spring-boot/4.0.0` | the oldest Spring Boot 4, with its Jackson 3.0 |
| `spring-boot/4.1.2` | the current Spring Boot 4, and the app `make dev` runs |
| `kotlin` | a `when` over `MonetaException` needs no `else`, and a nullable component is nullable in Kotlin |

Each Spring Boot version is only a `build.gradle.kts` naming it; the test is in `spring-boot/shared`. A context
builds `MonetaClient` as a bean and reads transactions from a local server, which is where both failures a
wrong Jackson causes show up, at class loading and on the first call. Another version is another folder with a
copy of that build file.

## Trying it against a real account

`make dev` starts `spring-boot/4.1.2`'s `DevApplication` on http://localhost:8080 with `MonetaClient` as a bean:
`/accounts`, and `/transactions?days=7` without `raw`, so the counterparty stays out of the page. The token
comes only from `MONETA_TOKEN`; with 1Password that is

    MONETA_TOKEN=op://<vault>/<item>/<field> op run -- make dev

Use a read-only token, and revoke it in Internet Banka when done.
