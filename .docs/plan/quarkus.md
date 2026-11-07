# Plan: a Quarkus extension

**A Quarkus application adds one dependency, sets `quarkus.moneta.*`, and injects `MonetaClient`, in JVM mode
and as a native image.** That is what the Spring Boot starter already does for Spring; this is the same promise
in Quarkus's own shape, which is an extension, not an auto-configuration.

Researched on 2026-11-07 against Quarkus 3.40. The sources are listed at the end.

## What an extension is, and what "ideal" means

A Quarkus extension is two artifacts. The **runtime** one holds what runs in the application: config, beans,
recorders. The **deployment** one runs only at build time, in `@BuildStep` methods, and tells Quarkus what to
register: beans, reflection for the native image, the feature name in the startup log. The runtime jar names
its deployment jar in `META-INF/quarkus-extension.properties`, and Quarkus pulls it in at build time. The
runtime may never depend on the deployment artifact.

Quarkus's [maturity matrix](https://quarkus.io/guides/extension-maturity-matrix) lists what a mature extension
does, and says itself that not every step applies to every extension. Going through it for this one:

| Item | Here | Why |
|------|------|-----|
| Works in JVM and dev mode | **yes, step 2** | the minimum |
| Works as a native application | **yes, step 1** | the main reason to write an extension rather than a CDI producer in the app, and the riskiest part, so first |
| Unified configuration (`@ConfigMapping`) | **yes, step 2** | validated at startup, documented, completed by the IDE |
| CDI beans | **yes, step 2** | `MonetaClient`, and `AccessToken` when a token is set; the application's own bean wins |
| TLS from the TLS registry | **yes, step 2** | the starter takes a Spring SSL bundle for mTLS; Quarkus's counterpart is `quarkus.tls.<name>` |
| Metrics | **yes, step 3** | the starter times every call when Micrometer is there; `quarkus-micrometer` is the same Micrometer |
| Basic Dev UI tile | **yes, free** | generated from the extension metadata |
| Logging | **nothing to do** | the client logs through `System.Logger`, which Quarkus routes into its own log |
| Virtual threads | **nothing to do** | the client is blocking on the JDK `HttpClient` and holds no lock across I/O; to be checked, not built |
| Dev Service | **no** | there is no MONETA container to start; a fake MONETA for dev mode is a later idea, see below |
| Health check | **no** | a readiness check that calls MONETA spends the merchant's rate limit on every probe |
| Tracing | **through Micrometer only** | `quarkus-opentelemetry` does not instrument the JDK `HttpClient`; Micrometer's observation gives spans where Micrometer Tracing is set up, as in Spring |
| Rich Dev UI, codestart, Mutiny API | **no** | nothing to show, and nobody has asked; a `-reactive` sibling only if someone does |
| `META-INF/quarkus-skill.md` | **later** | a few lines, once the API has settled |

## Decisions

- **Two modules**, `modules/moneta-api-quarkus` (runtime) and `modules/moneta-api-quarkus-deployment`, beside
  the starter, both published to GitHub Packages with the other jars. Quarkus resolves the deployment jar from
  the same repository, so it has to be published too.
- **Gradle, with the `io.quarkus.extension` plugin.** The guide calls it experimental and says it may lack
  what the Maven plugin has. What this extension needs from it is the descriptor and the deployment link, which
  it does. If step 2 finds a gap, writing `META-INF/quarkus-extension.properties` by hand is the fallback;
  a Maven sub-build in a Gradle repo is not.
- **Compiled against the oldest supported Quarkus, 3.33 LTS**, `compileOnly` the BOM, the way the starter is
  compiled against Spring Boot 3.5. Supported: the two LTS lines still maintained, **3.33** (until
  25 Mar 2027) and **3.40** (until 30 Sep 2027). Quarkus 4.0 is planned for 25 Nov 2026; it gets a testing
  project once it is out, not before.
- **Config is `quarkus.moneta.*`**, an `@ConfigMapping` in phase `RUN_TIME`, since a token or a host is known
  only when the application starts. Quarkus's guide asks extensions to put their options under
  `quarkus.<extension>`, so the prefix differs from the starter's `moneta.*`; the names under it stay the same:
  `token`, `base-uri`, `request-timeout`, `partner.identification-number`, `.business-name`,
  `.application-name`. The starter's `ssl.bundle` becomes `tls-configuration-name`, Quarkus's own name for it.
- **`MonetaClient` from a producer class in the runtime module**, registered with `AdditionalBeanBuildItem`:
  `@Singleton @DefaultBean`, closed by a `@Disposes` method on shutdown. `@DefaultBean` lets the application's
  own `MonetaClient` replace it, as `@ConditionalOnMissingBean` does in Spring. Only what the application set
  reaches the builder, so each default stays in the client.
- **`AccessToken` as a synthetic bean that is inactive without a token**: `SyntheticBeanBuildItem` with a
  recorder's `createWith` and `checkActive` returning inactive when `quarkus.moneta.token` is unset. A
  producer cannot do it: the token is runtime config, so `@IfBuildProperty` does not apply, and
  `@LookupIfProperty` only filters a programmatic `Instance` lookup, so `@Inject AccessToken` would still call
  the producer with no token. An inactive bean fails at the injection point with a message naming the
  property, and an application serving many merchants, which sets no token, starts.
- **A bad token or partner value stops the application at startup**, as in Spring: the client is created
  eagerly (`@Startup` on the producer), and an active `AccessToken` likewise.
- **TLS: `quarkus.moneta.tls-configuration-name`**, the name Quarkus extensions use for this, resolved
  through `TlsConfigurationRegistry` (in `quarkus-tls-registry-spi`, present in 3.33) into the client's
  `configureHttpClient`: `createSSLContext()` and, when set, the protocols and ciphers. Resolved when the
  client is built, so an unknown name stops the application, as an unknown Spring bundle does.
- **Customizers and decorators of the HTTP client** are the client's own `HttpClientCustomizer` and
  `HttpClientDecorator` beans, the pair the Spring and Micronaut modules read, through `Instance<…>` in
  `@Priority` order, applied after the TLS configuration, as in the starter.
- **Native image: nothing of its own.** The client jar carries its reachability metadata since
  [#48](https://github.com/landsman/moneta-api-java/pull/48), which every native build reads, so the
  extension needs no `ReflectiveClassBuildItem`. Jackson 3 needs nothing beyond it: `make native-test` checks
  that a native caller reads MONETA exactly as the JVM does.
- **Jackson needs nothing from the caller.** Quarkus is on Jackson 2, and the one jar the two share,
  `jackson-annotations`, is pinned to 2.21 by both supported BOMs (read from `quarkus-bom` 3.33.3 and 3.40.1),
  which is what Jackson 3.2 needs. Neither BOM manages `tools.jackson`, so the client's own Jackson 3 resolves.
  That is the case Spring Boot 3.5.12 and older get wrong; the testing projects keep it checked.

## Steps

Each step is its own pull request.

1. **Native image, in the client.** Done in [#48](https://github.com/landsman/moneta-api-java/pull/48): the
   reachability metadata in the client jar, a test that it lists every `*Json` record, and `make native-test`,
   a plain caller built with Mandrel and compared with the JVM, in its own CI job.
2. **JVM mode.** The two modules, the config mapping, the producer, the `AccessToken` synthetic bean, the TLS
   configuration name, the customizers and decorators, `FeatureBuildItem("moneta")`. Tests in the deployment
   module with `QuarkusUnitTest`: the bean appears; `AccessToken` is active only with a token, and injecting
   it without one fails naming the property; an application bean replaces the client; a bad token or an
   unknown TLS configuration fails the start; a customizer and a decorator run in order; `close()` is called
   on shutdown. One `@QuarkusIntegrationTest` built native, in the native CI job. A README for the extension
   with the property table, as the starter has.
3. **Metrics.** A decorator wrapping the client in `MicrometerHttpClient` with
   `uriMapper(MonetaClient::uriTemplate)` when `quarkus-micrometer` and `micrometer-java11` are present, off
   under the OpenTelemetry Java agent, replaceable, as in the starter.
4. **Testing projects.** `testing/quarkus/3.33` and `testing/quarkus/3.40`, each a caller's build with its own
   BOM and a `@QuarkusTest`, wired into `make build` beside `testing/spring-boot`. A Kotlin caller only if the
   Kotlin project's checks do not already cover what Quarkus adds.
5. **Docs.** `.docs/install.md` and the README name the extension; `.docs/compatibility.md` gets the Quarkus
   versions.

## Later, when someone needs it

- **Quarkiverse.** Listing in the Quarkus registry (`quarkus ext add`, code.quarkus.io) means a Quarkiverse
  repository under `io.quarkiverse.moneta`, Maven Central, an extension proposal issue in `quarkusio/quarkus`
  and the Quarkiverse build, which is Maven. It moves the code out of this repository, so it waits for users
  who ask.
- **A fake MONETA in dev mode**, as a Dev Service: dev mode then needs no token. Step 1's fake server is most
  of it.
- **Quarkus 4**, once released: a testing project, and a check whether its Jackson moves to 3, which would turn
  the Jackson section above into a question again.

## Sources

- [Writing your own extension](https://quarkus.io/version/3.40/guides/writing-extensions/), 3.40: modules,
  `@ConfigMapping` and phases, `AdditionalBeanBuildItem` and `SyntheticBeanBuildItem`,
  `ReflectiveClassBuildItem`, the Gradle plugin
- [Building my first extension](https://quarkus.io/version/3.40/guides/building-my-first-extension/): the Gradle
  extension plugin is experimental
- [Extension maturity matrix](https://quarkus.io/version/3.40/guides/extension-maturity-matrix/)
- [Quarkus releases](https://quarkus.io/releases): 3.40 LTS (30 Sep 2026), 3.33 LTS (25 Mar 2026), 4.0 planned
  for 25 Nov 2026
- [Quarkiverse checklist for new projects](https://hub.quarkiverse.io/checklistfornewprojects/)
- `quarkus-bom` 3.33.3 and 3.40.1 on Maven Central: `jackson-annotations` 2.21, no `tools.jackson`
- `quarkus-arc-deployment` and `quarkus-tls-registry-spi` 3.33.4 on Maven Central:
  `SyntheticBeanBuildItem.ExtendedBeanConfigurator#checkActive`, `TlsConfigurationRegistry`,
  `TlsConfiguration#createSSLContext`
