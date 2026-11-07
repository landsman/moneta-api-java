# Installing

All four jars are on this repository's [GitHub Packages](https://github.com/landsman?tab=packages&repo_name=moneta-api-java):

| Artifact | For |
|----------|-----|
| `cz.insuit:moneta-api` | the client, from plain Java or Kotlin |
| `cz.insuit:moneta-api-spring-boot-starter` | the client and its Spring Boot auto-configuration; brings `moneta-api` with it |
| `cz.insuit:moneta-api-play` | the client and its Play Framework module; brings `moneta-api` with it |
| `cz.insuit:moneta-api-micronaut` | the client and its Micronaut wiring; brings `moneta-api` with it |

**GitHub Packages asks for a token even for a public package.** A personal access token (classic) with the
`read:packages` scope is enough; keep it out of the build file.

## Gradle

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/landsman/moneta-api-java")
        credentials {
            username = providers.gradleProperty("gpr.user").get()
            password = providers.gradleProperty("gpr.key").get()
        }
    }
}

dependencies {
    implementation("cz.insuit:moneta-api-spring-boot-starter:0.1.0") // or -play, -micronaut, or moneta-api alone
}
```

with `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`, not in the project.

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
