# Kotlin and Spring Boot

## Kotlin

The JSpecify annotations make `iban` a `String?` and a `when` over `MonetaException` exhaustive without an
`else`, enforced from Kotlin 2.1. Kotlin 1.9, which Spring Boot 3 manages, only warns; add
`-Xjspecify-annotations=strict` to the compiler options to make it an error.

## Spring Boot 3

The library uses Jackson 3, which lives beside a Spring Boot 3 application's Jackson 2 without conflict, except
for one jar the two share: `com.fasterxml.jackson.core:jackson-annotations`. Jackson 3.2 needs 2.21 or newer,
and Spring Boot's BOM pins it lower up to 3.5.12, so the client fails to load with `NoClassDefFoundError`. On
those versions raise it in your own dependency management; Jackson 2.18 databind runs on it.

```xml
<!-- Maven, in <dependencyManagement><dependencies> -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
    <version>2.21</version>
</dependency>
```

```kotlin
// Gradle with io.spring.dependency-management
dependencyManagement { dependencies { dependency("com.fasterxml.jackson.core:jackson-annotations:2.21") } }
```

Spring Boot 3.5.13 and newer, and every Spring Boot 4, need nothing. `make build` checks all three cases, and a
Kotlin caller, in [testing/](../testing/README.md).
