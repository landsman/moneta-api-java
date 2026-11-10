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

## Use

```java
try (var moneta = new MonetaClient()) { // one per application; close it when the application stops
    var me = moneta.with(new AccessToken(System.getenv("MONETA_TOKEN")));
    me.transactions().between(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).incoming().booked().list();
}
```

The rest is in [Using the client](../../.docs/usage.md).
