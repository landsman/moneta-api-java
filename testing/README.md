# Testing

Projects that use the library the way a caller's build would, so a version a caller is likely to have cannot
break it unnoticed. `make build` runs them with the library's own tests, which stay in each module under `modules/`.

| Project | Proves |
|---------|--------|
| `spring-boot/3.5.12` | the `jackson-annotations` 2.21 fix from [compatibility](../.docs/compatibility.md) works on the last Spring Boot that needs it |
| `spring-boot/3.5.16` | Spring Boot 3.5.13 and newer need nothing |
| `spring-boot/4.0.0` | the oldest Spring Boot 4, with its Jackson 3.0 |
| `spring-boot/4.1.2` | the current Spring Boot 4 |
| `play/3.0-scala-2.13`, `play/3.0-scala-3` | the Play module on Play 3.0 for both Scalas, with the Jackson 2.14 it pins |
| `play/2.9` | the same jar on Play 2.9, the Akka line |
| `micronaut/4.10.14` | the Micronaut module on the Micronaut it is compiled against, with its BOM |
| `micronaut/5.2.5` | the same jar, compiled for Micronaut 4, loads and binds on Micronaut 5; runs on JDK 25 whatever `-PtestsJdk` says |
| `quarkus/3.33.4` | the Quarkus extension on the Quarkus it is compiled against, with the platform BOM a caller imports |
| `quarkus/3.40.1` | the same jar on the current LTS; `make native-test` also builds it native, with the Jackson fix from [compatibility](../.docs/compatibility.md#quarkus), and runs it on Linux |
| `kotlin` | a `when` over `MonetaException` needs no `else`, and a nullable component is nullable in Kotlin |
| `native` | the client's metadata is enough for a native image: built by `make native-test`, not `make build`, it prints the same as on the JVM |

Each Spring Boot version is a folder whose `build.gradle.kts` only imports its BOM; what they share is in
`spring-boot/build.gradle.kts`, and the test is in `spring-boot/shared`. A context
with only the starter on it gets `MonetaClient` and `AccessToken` from its auto-configuration and reads transactions from a local server, which is where both failures a
wrong Jackson causes show up, at class loading and on the first call. Another version is another folder with that
one line, and an entry in `settings.gradle.kts`. Play and Micronaut are laid out the same, around `play/shared` and
`micronaut/shared`. Quarkus is not: its Gradle plugin carries the version too. The Micronaut projects
keep Micrometer off their classpath: the module names its types in a method signature, and these projects
are what proves it loads without them.

Both also log the client at DEBUG, since writing that line is the one place it serialises: a BOM pinning
`jackson-annotations` 2.21 under databind 3.2 once broke every call there. The Micronaut projects carry
`slf4j-jdk-platform-logging` and a `logback.xml`, as the module README tells an application to, and check the line
reaches Logback through `logger.levels` alone; without the provider it does not, Micronaut leaves
`java.util.logging` alone.

