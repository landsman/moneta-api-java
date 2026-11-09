# Installing

All six jars are on this repository's [GitHub Packages](https://github.com/landsman?tab=packages&repo_name=moneta-api-java):

| Artifact | For |
|----------|-----|
| `cz.insuit:moneta-api` | the client, from plain Java or Kotlin |
| `cz.insuit:moneta-api-spring-boot-starter` | the client and its Spring Boot auto-configuration; brings `moneta-api` with it |
| `cz.insuit:moneta-api-play` | the client and its Play Framework module; brings `moneta-api` with it |
| `cz.insuit:moneta-api-micronaut` | the client and its Micronaut wiring; brings `moneta-api` with it |
| `cz.insuit:moneta-api-quarkus` | the client and its Quarkus extension; brings `moneta-api` with it, and Quarkus fetches `moneta-api-quarkus-deployment` from the same repository at build time |

**GitHub Packages asks for a token even for a public package**, with the `read:packages` scope. It checks only
the token: any non-empty user name passes.

## Gradle

With the [GitHub CLI](https://cli.github.com/) signed in, the build can read gh's own token on every run, so no
copy of it sits on disk and nothing has to be renewed by hand. Give gh the scope once:

```sh
gh auth refresh -h github.com -s read:packages
```

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/landsman/moneta-api-java")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse("gh").orNull
            password = providers.gradleProperty("gpr.key").orElse(gh("auth", "token")).orNull
        }
        content { includeGroup("cz.insuit") }
    }
}

dependencies {
    implementation("cz.insuit:moneta-api-spring-boot-starter:0.1.0") // or -play, -micronaut, -quarkus, or moneta-api alone
}

// gh's token, or nothing when gh is missing or signed out: the build then gets GitHub's 401, not a crash.
fun gh(vararg args: String): Provider<String> =
    provider {
        runCatching {
            providers.exec {
                commandLine("gh", *args)
                isIgnoreExitValue = true
            }.standardOutput.asText.get().trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
```

- **Without gh**, a personal access token (classic) with only `read:packages` (fine-grained tokens cannot read
  packages) as `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`, never in the project. They win over gh.
- **In CI**, pass the token from a secret, e.g. `.orElse(providers.environmentVariable("GITHUB_PACKAGES_TOKEN"))`
  before the gh fallback, so a runner never calls gh.
- **The configuration cache** stores the resolved token, encrypted, under the project's `.gradle`; with it on,
  "no copy on disk" no longer holds.

## Maven

```xml
<repositories>
    <repository>
        <id>github-moneta</id>
        <url>https://maven.pkg.github.com/landsman/moneta-api-java</url>
    </repository>
</repositories>

<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>moneta-api-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

and in `~/.m2/settings.xml` a `<server>` with the same `id`, your GitHub user and the token.

## Snapshots

None are published: only a tagged release is. To try what is not released yet, `make publish-local` in a checkout
installs every jar into `~/.m2` as `0.1.0-SNAPSHOT`, where `mavenLocal()` finds them.
