# Plan: a Micronaut integration

**A third module, `moneta-api-micronaut`, gives a Micronaut application what the starter gives a Spring Boot
one: set `moneta.*`, inject `MonetaClient`.** The same property names and behaviour, except `moneta.ssl.bundle`,
which names a Spring Boot SSL bundle and has no Micronaut counterpart. A spike decides first which Micronaut
major the module is compiled against.

Researched 2026-11-07; sources at the end.

## What Micronaut is today

| | Micronaut 4.10 | Micronaut 5 |
|---|---|---|
| Released | 4.10.14 on 2026-05-12, still patched | 5.0.0 on 2026-05-20; 5.2.5 is the latest on Maven Central |
| JDK | 17 and 21 | **25 baseline**, its jars are Java 25 bytecode |
| Jackson its BOM manages | 2.21.2, `jackson-annotations` 2.21 | 3.2.3, `jackson-annotations` 2.22 |

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
| `@ConditionalOnClass`, `@ConditionalOnMissingClass` | `@Requires(classes = …)`, `@Requires(missingClasses = "…")` |
| `ObjectProvider<…>.orderedStream()` | an injected `List<…>`, sorted by `@Order` from `io.micronaut.core.annotation` |
| closed on shutdown because the bean is `AutoCloseable` | **not closed** unless `@Bean(preDestroy = "close")`; implementing `AutoCloseable` is not enough |
| beans eager, a bad token or partner header stops startup | singletons lazy; `@Context` makes one eager |
| `spring-boot-configuration-processor` metadata | written by the same processor, defaults taken from `@Bindable(defaultValue)` |

The shape, then — a sketch; how a nested record binds, and whether `missingBeans` on the method that makes that
same bean excludes itself, are the first things the spike checks:

```java
@ConfigurationProperties("moneta")
public record MonetaConfiguration(
        @Nullable String token, @Nullable URI baseUri, @Nullable Duration requestTimeout, @Nullable Partner partner) {
    @ConfigurationProperties("partner")
    public record Partner(String identificationNumber, String businessName, String applicationName) {}
}

@Factory
class MonetaFactory {
    // @Context on both, so what the client refuses stops startup, as it does under Spring Boot.
    @Context
    @Bean(preDestroy = "close")
    @Requires(missingBeans = MonetaClient.class)
    MonetaClient monetaClient(
            MonetaConfiguration configuration,
            List<HttpClientCustomizer> customizers,
            List<HttpClientDecorator> decorators) { /* as MonetaAutoConfiguration */ }

    @Context
    @Requires(property = "moneta.token")
    AccessToken monetaAccessToken(MonetaConfiguration configuration) { ... }

    // Same conditions as the starter's: micrometer-java11 present, no OpenTelemetry agent, a MeterRegistry bean.
    @Singleton
    @Named("monetaHttpClientObservation")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Requires(classes = MicrometerHttpClient.class, beans = MeterRegistry.class)
    @Requires(missingClasses = "io.opentelemetry.javaagent.bootstrap.AgentInitializer")
    HttpClientDecorator monetaHttpClientObservation(MeterRegistry meters, Optional<ObservationRegistry> observations) { ... }
}
```

The official modules (micronaut-email's Postmark sender, for one) are mutable classes with setters and an
`enabled` flag; a record works too and matches the starter.

## Customizers and decorators belong to the client

`MonetaHttpClientCustomizer` and `MonetaHttpClientDecorator` live in `cz.insuit.moneta.spring`, yet name nothing
from Spring: one changes an `HttpClient.Builder`, the other wraps an `HttpClient`. Copying them into a
`micronaut` package would give two pairs that drift. They move to `cz.insuit.moneta` as `HttpClientCustomizer`
and `HttpClientDecorator`, in their own PR before this module; the starter's go in the same PR, which the
pre-1.0 version allows and its release notes say.

mTLS then needs no `moneta.ssl.*` under Micronaut: an application's `HttpClientCustomizer` bean sets the
`SSLContext`, from its own key store or from Micronaut's SSL configuration.

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

**Result (2026-11-07):** it loads and binds on Micronaut 5 (core 5.2.22, BOM 5.2.5), so one module. The spike also
found that the nested `Partner` record needs `@Requires(property = "moneta.partner")`, or Micronaut builds it
when nothing is set and fails, and that `5s` reads as a `Duration` only with `micronaut-context` present, which
every Micronaut application has. `missingBeans` on the method that makes the same bean does exclude itself.

Either way the 5.x test project runs on JDK 25 and the 4.10 one on 21, so CI runs `testing/micronaut/5.x` with
`-PtestsJdk=25`.

## Jackson

The client uses Jackson 3 as an implementation dependency, which is where Micronaut 5 is too. A build importing
Micronaut 5.2.5's BOM resolves `jackson-databind` to 3.2.3 instead of the client's 3.2.4, which holds: the
client runs on 3.0.4 (`jacksonFloorTest`).

Micronaut 4 brings Jackson 2 or Micronaut Serialization, and the client's Jackson 3 sits beside either. The one
jar they share is `jackson-annotations`, the failure `.docs/compatibility.md` describes for Spring Boot 3.5.12;
4.10.14's BOM manages 2.21, which is enough. Both test projects import the BOM, so an older 4.10.x or a later BOM
that pins it lower fails there first.

## GraalVM native image

The client binds its `*Json` records by reflection through its own `ObjectMapper`, so under any native image —
Spring Boot's, Micronaut's, plain — it fails unless `moneta-api` ships
`META-INF/native-image/cz.insuit/moneta-api/reachability-metadata.json` for them. That is the client's gap, not
this module's, and a PR of its own that proves it with one `nativeTest` run.

## Steps

1. **Move the customizer and decorator** to the client, as above. Its own PR.
2. **Spike** — the test project above, module compiled against 4.10. Decides the table.
3. **The module** — `modules/moneta-api-micronaut`, package `cz.insuit.moneta.micronaut`: the record, the
   factory, `package-info.java` with `@NullMarked`. Plain `java-library`, not the `io.micronaut.library` plugin:
   that one applies the GraalVM plugin and the platform BOM, and a library should not hand its BOM to the caller.

   ```kotlin
   val micronaut = dependencies.platform("io.micronaut.platform:micronaut-platform:<version>")
   dependencies {
       api(project(":modules:moneta-api"))
       api("org.jspecify:jspecify:1.0.1")
       compileOnly(micronaut)
       compileOnly("io.micronaut:micronaut-inject")
       compileOnly("io.micronaut:micronaut-core")
       compileOnly("jakarta.inject:jakarta.inject-api")
       compileOnly("io.micrometer:micrometer-java11")
       compileOnly("io.micrometer:micrometer-core")
       compileOnly("io.micrometer:micrometer-observation")
       annotationProcessor(micronaut)
       annotationProcessor("io.micronaut:micronaut-inject-java")
   }
   ```

   Every annotation it uses is declared, or the dependency analysis fails the build. `-processing` is already off
   in `-Xlint`; Micronaut's processor may need more lint exceptions under `-Werror`, and NullAway has to read its
   generated classes as not `@NullMarked`.
4. **Its test** — `ApplicationContext.run(Map.of("moneta.token", ...))`: the client and token beans exist, the
   app's own `MonetaClient` wins, a bad token and a bad partner header each fail `run`, customizers and
   decorators apply in order, the observation decorator appears only with a `MeterRegistry`, the client is closed
   with the context.
5. **`testing/micronaut/<version>`** — the counterpart of `testing/spring-boot/shared`: only the module on the
   classpath, the BOM imported, transactions read from a local server. One folder per supported version.
6. **Docs** — the module's README (the starter's property table without `moneta.ssl.*`), a row in the root README
   and in `.docs/install.md`, Micronaut in `.docs/compatibility.md`.
7. **Publishing** — the release workflow publishes every `:modules:*` jar already; check the third one appears.

## Not planned

- **`moneta.ssl.*`.** It names a Spring Boot SSL bundle; a customizer covers mTLS, as above.
- **A Micronaut café in `examples/`.** The testing project proves the wiring; a second example app doubles
  `make dev` for no new information.
- **A health indicator.** It would spend a call with a merchant's token on every probe. Add it when an app asks.
- **Micronaut's declarative HTTP client** (`@Client`) in place of the JDK one. The client stays framework-free;
  the module only wires it.

## Still open

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
- The platform BOMs on Maven Central:
  [4.10.14](https://repo1.maven.org/maven2/io/micronaut/platform/micronaut-platform/4.10.14/micronaut-platform-4.10.14.pom),
  [5.2.5](https://repo1.maven.org/maven2/io/micronaut/platform/micronaut-platform/5.2.5/micronaut-platform-5.2.5.pom)
- [Micronaut Gradle plugin](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [micronaut-email's Postmark module](https://github.com/micronaut-projects/micronaut-email/tree/3.3.x/email-postmark)
