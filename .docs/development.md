# Development

`make build` compiles, tests and packages; `make` lists the targets. A JDK 21 or newer is enough, and
`mise.toml` pins the one used here.

It also fails when a module under `modules/` declares a dependency it does not use, or uses one it only gets
transitively. What to change is in `build/reports/dependency-analysis/build-health-report.txt`.

What ships is in [modules/](../modules): the client, `moneta-api`, its
[Spring Boot starter](../modules/moneta-api-spring-boot-starter/README.md), its
[Play module](../modules/moneta-api-play/README.md), its
[Micronaut module](../modules/moneta-api-micronaut/README.md) and its
[Quarkus extension](../modules/moneta-api-quarkus/README.md), each with its own tests. Projects that use them
the way a caller's build would, on several Spring Boot, Play, Micronaut and Quarkus versions and from Kotlin, are in
[testing/](../testing/README.md).
`make dev` runs [an example café app](../examples/cafe/README.md) against a real account: it issues bills as QR
payments and finds them paid. Try it by hand at http://localhost:8080/scalar, an API reference to call each
endpoint from the browser.

## Trying the live API

`make peek` prints the account's movements as MONETA sends them, every field included, with whatever names a
person masked: the parties, the message, the reference. It is how a new field or a new kind of movement is found
before the client maps it. `tools/peek` is internal and never published.

    make peek                                 # the last week
    make peek FROM=2026-11-01 TO=2026-11-07   # a range; SHAPE=1 shows masked text as x and 9 instead of a length
    make peek-accounts
    make peek-balance
    make record-fixture N=3 NAME=booked-card FROM=2026-11-01 TO=2026-11-07  # movement 3 of that make peek

The token is read from 1Password for each run: `op run` resolves `MONETA_OP`, a secret reference that defaults to
the maintainer's item, hands the value to that one process and masks it in the output. No file holds it, and
Gradle only builds the tool, so it does not stay in a daemon. Point `MONETA_OP=op://<vault>/<item>/<field>` at
your own.

`record-fixture` replaces people by the rules in
[the fixtures' README](../modules/moneta-api/src/test/resources/moneta/README.md) and refuses a value under the
parties it has no rule for, or any value `make peek` would mask that is not a fixed one. It needs the dates `make
peek` numbered with, since a range ending today renumbers when a payment arrives. It never overwrites a fixture. Read the file whole before committing it: the
repository is public.

## Releasing

A release is made by hand, and only a tag makes one: merging to `main` publishes nothing. A tag `vX.Y.Z` on
`main` releases `X.Y.Z`. `make release` does all of what follows: it asks for the tag, suggesting the next
patch, refuses one that exists or a checkout that is not a clean `origin/main`, asks once more, then tags,
waits for the publish and writes the release page, a pre-release while it is 0.x. By hand:

    git switch main && git pull --ff-only              # the commit to release, with CI green on it
    git tag -a v0.1.0 -m "v0.1.0"                      # annotated, so it records who tagged it and when
    git push origin v0.1.0                             # this push starts the release

[release.yml](../.github/workflows/release.yml) then builds, tests and publishes every jar to GitHub Packages
(`gh run watch` follows it). Once it is green, write the release page for the tag, with what changed for a user:

    gh release create v0.1.0 --verify-tag --generate-notes     # --prerelease while it is 0.x

`--verify-tag` refuses to create a tag that was not pushed, so the page never starts a release of its own. A
version, once published, cannot be published again; the next fix is the next tag. `make publish` is what the
workflow runs; from a laptop it needs `GITHUB_ACTOR` and a `GITHUB_TOKEN` with `write:packages`, and is not the
intended way to release.
