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

A release is made by hand, and only a tag makes one: merging to `main` publishes nothing. A tag `vX.Y.Z` on
`main` releases `X.Y.Z`. `make release` does all of what follows: it asks for the tag, suggesting the next
patch, refuses one that exists or a checkout that is not a clean `origin/main`, asks once more, then tags,
waits for the publish and writes the release page, a pre-release while it is 0.x. By hand:

    git switch main && git pull --ff-only              # the commit to release, with CI green on it
    git tag -a v0.1.0 -m "v0.1.0"                      # annotated, so it records who tagged it and when
    git push origin v0.1.0                             # this push starts the release

[release.yml](../.github/workflows/release.yml) then builds, tests and publishes both jars to GitHub Packages
(`gh run watch` follows it). Once it is green, write the release page for the tag, with what changed for a user:

    gh release create v0.1.0 --verify-tag --generate-notes     # --prerelease while it is 0.x

`--verify-tag` refuses to create a tag that was not pushed, so the page never starts a release of its own. A
version, once published, cannot be published again; the next fix is the next tag. `make publish` is what the
workflow runs; from a laptop it needs `GITHUB_ACTOR` and a `GITHUB_TOKEN` with `write:packages`, and is not the
intended way to release.
