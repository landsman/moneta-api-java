# Plan: a Micronaut integration

**A third module, `moneta-api-micronaut`, gives a Micronaut application what the starter gives a Spring Boot
one: set `moneta.*`, inject `MonetaClient`.** Same property names, same behaviour, so the starter's README and
`make dev` habits carry over. A spike decides first which Micronaut major the module is compiled against.

Researched 2026-11-07; sources at the end.

## What Micronaut is today

| | Micronaut 4.10 | Micronaut 5 |
|---|---|---|
| Released | 4.10.14 on 2026-05-12, still patched | 5.0.0 on 2026-05-20; docs "latest" points at 5.2.x |
| JDK | 17 and 21 | **25 baseline**, its jars are Java 25 bytecode |
| Jackson in `micronaut-jackson-databind` | 2 | **3**, the BOM managed 3.1.3 at 5.0.0 |
| Kotlin | kapt or KSP | 2.3 baseline, KSP |

No end-of-support date for 4.x was found; the Java 25 post says only that 4.x "will continue to support Java 17
and Java 21".

## What an integration looks like there

Micronaut does at compile time what Spring Boot does at startup: `micronaut-inject-java`, an annotation
processor, writes the bean definitions into the jar. Nothing is scanned at runtime, which is also why the
module has to be compiled by Micronaut's processor and cannot be a hand-written registration file.

| Spring Boot starter | Micronaut |
|---|---|
| `@AutoConfiguration` + `AutoConfiguration.imports` | `@Factory`; the processor registers it |
| `@ConfigurationProperties` record | the same annotation, `io.micronaut.context.annotation`, on a record; bound without reflection |
| `@ConditionalOnProperty("moneta.token")` | `@Requires(property = "moneta.token")` |
| `@ConditionalOnMissingBean` | `@Requires(missingBeans = MonetaClient.class)`, or the app's bean with `@Replaces` |
| closed on shutdown because the bean is `AutoCloseable` | **not closed** unless `@Bean(preDestroy = "close")`; implementing `AutoCloseable` is not enough |
| beans eager, a bad token stops startup | singletons lazy; `@Context` makes one eager |
| `spring-boot-configuration-processor` metadata | written by the same processor, defaults taken from `@Bindable(defaultValue)` |

The shape, then — a sketch; how a nested record binds is the first thing the spike checks:

```java
@ConfigurationProperties("moneta")
public record MonetaConfiguration(
        @Nullable String token, @Nullable URI baseUri, @Nullable Duration requestTimeout, @Nullable Partner partner) {
    @ConfigurationProperties("partner")
    public record Partner(String identificationNumber, String businessName, String applicationName) {}
}

@Factory
class MonetaFactory {
    @Singleton
    @Bean(preDestroy = "close")
    @Requires(missingBeans = MonetaClient.class)
    MonetaClient monetaClient(MonetaConfiguration configuration) { /* as MonetaAutoConfiguration */ }

    @Context // so a token the client refuses stops startup, as it does under Spring Boot
    @Requires(property = "moneta.token")
    AccessToken monetaAccessToken(MonetaConfiguration configuration) { ... }
}
```

The official modules (micronaut-email's Postmark sender, for one) are mutable classes with setters and an
`enabled` flag. A record is supported and matches the starter; an `enabled` flag is not needed while the client
does nothing until called.

## The decision the spike makes: which major to compile against

The starter is compiled against the oldest Spring Boot it supports and tested on each newer one. The same here
would be: compile against 4.10 on release 21, run on 4 and 5. Two things are unknown:

- **Does a bean definition compiled by Micronaut 4 load on Micronaut 5?** Micronaut 3 libraries did not load on
  4, "the library must be recompiled" (core `breaks.adoc`). For 5 the notes say nothing either way; 5.2.1 states
  that definitions compiled by an earlier 5.x keep working until recompiled.
- **Compiling against Micronaut 5 needs release 25** for that module, while the rest of the build stays on 21
  (`options.release = 21` in the root `build.gradle.kts`). Possible, but then the module's jar needs Java 25.

Spike: `testing/micronaut/5.x` running a module compiled against 4.10. Then:

| Spike result | Ship |
|---|---|
| it loads and binds on 5 | one module, compiled against 4.10, release 21, tested on 4.10 and 5.x |
| it does not | `moneta-api-micronaut` for 5 (release 25); a `-micronaut4` twin only when someone asks for it |

## Jackson

The client uses Jackson 3 as an implementation dependency, which is where Micronaut 5 is too, so there is no
second Jackson as under Spring Boot 3. The risk is the version: a Maven build importing Micronaut's BOM
resolves `jackson-databind` to Micronaut's 3.1.x instead of the client's 3.2.4. The client already runs on
3.0.4 (`jacksonFloorTest`), so that holds; what would break it is databind 3.2 beside `jackson-annotations`
older than 2.21, the same failure `.docs/compatibility.md` describes for Spring Boot 3.5.12. The testing
project imports the BOM to catch it.

Micronaut 4 brings Jackson 2 or Micronaut Serialization, and the client's Jackson 3 sits beside either, as it
does under Spring Boot 3.

## GraalVM native image

Micronaut 5 removed its Jackson bean introspection module, so Jackson databind needs reflection metadata for
every type it binds. The client binds its own records with Jackson, so under native image it fails unless
`moneta-api` ships `META-INF/native-image/cz.landsman/moneta-api/reachability-metadata.json` for them. That helps
Spring Boot native too, so it belongs in the client, as its own PR, not in this module. Nothing in the docs read
says more; the PR proves it with one `nativeTest` run.

## Steps

1. **Spike** — the test project above, module compiled against 4.10. Decides the table.
2. **The module** — `modules/moneta-api-micronaut`, package `cz.landsman.moneta.micronaut`: the record, the
   factory, `package-info.java` with `@NullMarked`. Plain `java-library`, not the `io.micronaut.library` plugin:
   that one applies the GraalVM plugin and the platform BOM, and a library should not hand its BOM to the caller.

   ```kotlin
   val micronaut = dependencies.platform("io.micronaut.platform:micronaut-platform:<version>")
   dependencies {
       api(project(":modules:moneta-api"))
       compileOnly(micronaut)
       compileOnly("io.micronaut:micronaut-inject")
       annotationProcessor(micronaut)
       annotationProcessor("io.micronaut:micronaut-inject-java")
   }
   ```

   `-processing` is already off in `-Xlint`; Micronaut's processor may need more lint exceptions under
   `-Werror`, and NullAway has to read its generated classes as not `@NullMarked`.
3. **Its test** — `ApplicationContext.run(Map.of("moneta.token", ...))`: the client and token beans exist, the
   app's own `MonetaClient` wins, a bad token fails `run`, the client is closed with the context.
4. **`testing/micronaut/<version>`** — the counterpart of `testing/spring-boot/shared`: only the module on the
   classpath, the BOM imported, transactions read from a local server. One folder per supported version.
5. **Docs** — the module's README (the property table is the starter's), a row in the root README and in
   `.docs/install.md`, Micronaut in `.docs/compatibility.md`.
6. **Publishing** — the release workflow publishes every `:modules:*` jar already; check the third one appears.

## Not planned

- **A Micronaut café in `examples/`.** The testing project proves the wiring; a second example app doubles
  `make dev` for no new information.
- **A health indicator.** It would spend a call with a merchant's token on every probe. Add it when an app asks.
- **Micronaut's declarative HTTP client** (`@Client`) in place of the JDK one. The client stays framework-free;
  the module only wires it.

## Still open

- The exact current 5.x and its BOM's Jackson version (5.2.x seen in the docs redirect, no release post read).
- The name and location of the generated configuration metadata, and whether IntelliJ completes `moneta.*` from it.
- Whether the 4.x line has an announced end.

## Sources

- [Micronaut 5.0.0 released](https://micronaut.io/2026/05/20/micronaut-framework-5-0-0-released/)
- [Micronaut 5 with a Java 25 baseline](https://micronaut.io/2026/04/27/micronaut-framework-5-0-with-java-25-baseline/)
- [Micronaut 5 with Jackson 3](https://micronaut.io/2026/05/01/micronaut-framework-5-with-jackson-3/)
- [Update to Micronaut 5, the core wiki](https://github.com/micronaut-projects/micronaut-core/wiki/Update-to-Micronaut-5)
- Core guide sources, 5.2.x: [breaks](https://github.com/micronaut-projects/micronaut-core/blob/5.2.x/src/main/docs/guide/appendix/breaks.adoc),
  [immutable configuration](https://github.com/micronaut-projects/micronaut-core/blob/5.2.x/src/main/docs/guide/config/immutableConfig.adoc),
  [conditional beans](https://github.com/micronaut-projects/micronaut-core/blob/5.2.x/src/main/docs/guide/ioc/conditionalBeans.adoc),
  [factories](https://github.com/micronaut-projects/micronaut-core/blob/5.2.x/src/main/docs/guide/ioc/factories.adoc),
  [life cycle](https://github.com/micronaut-projects/micronaut-core/blob/5.2.x/src/main/docs/guide/ioc/lifecycle.adoc),
  [replacing beans](https://github.com/micronaut-projects/micronaut-core/blob/5.2.x/src/main/docs/guide/ioc/replaces.adoc)
- [Micronaut Gradle plugin](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [micronaut-email's Postmark module](https://github.com/micronaut-projects/micronaut-email/tree/3.3.x/email-postmark)
