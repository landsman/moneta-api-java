# Development

`make build` compiles, tests and packages; `make` lists the targets. A JDK 21 or newer is enough, and
`mise.toml` pins the one used here.

What ships is in [modules/](../modules): the client, `moneta-api`, and its
[Spring Boot starter](../modules/moneta-api-spring-boot-starter/README.md), each with its own tests. Projects
that use them the way a caller's build would, on several Spring Boot versions and from Kotlin, are in
[testing/](../testing/README.md).
`make dev` runs [an example café app](../examples/cafe/README.md) against a real account.
