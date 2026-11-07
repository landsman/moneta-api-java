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
| `kotlin` | a `when` over `MonetaException` needs no `else`, and a nullable component is nullable in Kotlin |
| `native` | the client's metadata is enough for a native image: built by `make native-test`, not `make build`, it prints the same as on the JVM |

Each Spring Boot version is only a `build.gradle.kts` naming it; the test is in `spring-boot/shared`. A context
with only the starter on it gets `MonetaClient` and `AccessToken` from its auto-configuration and reads transactions from a local server, which is where both failures a
wrong Jackson causes show up, at class loading and on the first call. Another version is another folder with a
copy of that build file. The Micronaut projects are laid out the same, around `micronaut/shared`,
and keep Micrometer off their classpath: the module names its types in a method signature, and these projects
are what proves it loads without them.

Both also log the client at DEBUG, since writing that line is the one place it serialises: a BOM pinning
`jackson-annotations` 2.21 under databind 3.2 once broke every call there. The Micronaut projects carry
`slf4j-jdk-platform-logging` and a `logback.xml`, as the module README tells an application to, and check the line
reaches Logback through `logger.levels` alone; without the provider it does not, Micronaut leaves
`java.util.logging` alone.

