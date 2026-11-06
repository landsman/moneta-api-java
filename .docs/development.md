# Development

`make build` compiles, tests and packages; `make` lists the targets. A JDK 21 or newer is enough, and
`mise.toml` pins the one used here.

What ships is in [modules/](../modules): the client, `moneta-api`, and its
[Spring Boot starter](../modules/moneta-api-spring-boot-starter/README.md), each with its own tests. Projects
that use them the way a caller's build would, on several Spring Boot versions and from Kotlin, are in
[testing/](../testing/README.md).
`make dev` runs [an example café app](../examples/cafe/README.md) against a real account: it issues bills as QR
payments and finds them paid. Try it by hand at http://localhost:8080/scalar, an API reference to call each
endpoint from the browser.

## Releasing

A tag `vX.Y.Z` on `main` releases `X.Y.Z`:

    git tag v0.1.0 && git push origin v0.1.0

[release.yml](../.github/workflows/release.yml) then builds, tests and publishes both jars to GitHub Packages.
Every push to `main` publishes the next snapshot the same way. `make publish` is what it runs; from a laptop it
needs `GITHUB_ACTOR` and a `GITHUB_TOKEN` with `write:packages`, and is not the intended way to release.
A version, once published, cannot be published again; the next fix is the next tag.
