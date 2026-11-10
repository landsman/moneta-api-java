# moneta-api

The MONETA API client for Java 21+ and Kotlin, without a framework. With Spring Boot, Play, Micronaut or Quarkus,
take that module instead; it brings this one.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("cz.insuit:moneta-api:0.2.0")
}
```

<details><summary>Maven</summary>

```xml
<!-- in <dependencies> -->
<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>moneta-api</artifactId>
    <version>0.2.0</version>
</dependency>
```

</details>

GitHub Packages asks for a token even for a public package: the repository block and the token are in
[Installing](../../.docs/install.md).

## With a coding assistant

Paste this into it:

```text
Add the MONETA API client to this plain Java or Kotlin project.

1. Read https://github.com/landsman/moneta-api-java/blob/main/llms.txt and https://github.com/landsman/moneta-api-java/blob/main/modules/moneta-api/README.md first. Do not guess the API.
2. Dependency: cz.insuit:moneta-api:0.2.0 from GitHub Packages, with Gradle or Maven, as
   https://github.com/landsman/moneta-api-java/blob/main/.docs/install.md shows. Never write a token into the project.
3. Configuration: the token from the MONETA_TOKEN environment variable, wrapped in AccessToken.
4. Code: make one MonetaClient for the application and close it when the application stops. Bind the token with moneta.with(token), read with
   transactions().between(from, to), narrowed by incoming(), outgoing(), transfers(),
   cards(), booked() or pending(), then list().
5. Act on booked movements only, unless I ask for pending ones.
```

## Use

```java
try (var moneta = new MonetaClient()) { // one per application; close it when the application stops
    var me = moneta.with(new AccessToken(System.getenv("MONETA_TOKEN")));
    me.transactions().between(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).incoming().booked().list();
}
```

The rest is in [Using the client](../../.docs/usage.md).
