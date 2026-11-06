# Development

`make build` compiles, tests and packages; `make` lists the targets. A JDK 21 or newer is enough, and
`mise.toml` pins the one used here.

The library's own tests are in `src/test`; projects that use it the way a caller's build would, on several
Spring Boot versions and from Kotlin, are in [testing/](../testing/README.md). `make dev` runs one of them as an
app against a real account; that README says how.
