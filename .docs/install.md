# Installing

All six jars are on [Maven Central](https://central.sonatype.com/namespace/cz.insuit), so no repository block or
token is needed:

| Artifact | For |
|----------|-----|
| `cz.insuit:moneta-api` | the client, from plain Java or Kotlin |
| `cz.insuit:moneta-api-spring-boot-starter` | the client and its Spring Boot auto-configuration; brings `moneta-api` with it |
| `cz.insuit:moneta-api-play` | the client and its Play Framework module; brings `moneta-api` with it |
| `cz.insuit:moneta-api-micronaut` | the client and its Micronaut wiring; brings `moneta-api` with it |
| `cz.insuit:moneta-api-quarkus` | the client and its Quarkus extension; brings `moneta-api` with it, and Quarkus fetches `moneta-api-quarkus-deployment` from Central at build time |

## Gradle

```kotlin
dependencies {
    implementation("cz.insuit:moneta-api-spring-boot-starter:0.6.0") // or -play, -micronaut, -quarkus, or moneta-api alone
}
```

## Maven

```xml
<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>moneta-api-spring-boot-starter</artifactId>
    <version>0.6.0</version>
</dependency>
```

## Older versions

0.5.0 and earlier are only on this repository's
[GitHub Packages](https://github.com/landsman?tab=packages&repo_name=moneta-api-java), which asks for a token with
the `read:packages` scope even for a public package. The
[previous version of this page](https://github.com/landsman/moneta-api-java/blob/5e7336a/.docs/install.md) shows the
repository block that needs.

## Snapshots

None are published: only a tagged release is. To try what is not released yet, `make publish-local` in a checkout
installs every jar into `~/.m2` as `0.6.0-SNAPSHOT`, where `mavenLocal()` finds them.
