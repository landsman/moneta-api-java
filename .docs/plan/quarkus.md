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
| Works in JVM and dev mode | **yes, step 1** | the minimum |
| Works as a native application | **yes, step 2** | the main reason to write an extension rather than a CDI producer in the app |
| Unified configuration (`@ConfigMapping`) | **yes, step 1** | validated at startup, documented, completed by the IDE |
| CDI beans | **yes, step 1** | `MonetaClient`, and `AccessToken` when a token is set; the application's own bean wins |
| Basic Dev UI tile | **yes, free** | generated from the extension metadata |
| Logging | **nothing to do** | the client logs through `System.Logger`, which Quarkus routes into its own log |
| Virtual threads | **nothing to do** | the client is blocking on the JDK `HttpClient` and holds no lock across I/O; to be checked, not built |
| Dev Service | **no** | there is no MONETA container to start; a fake MONETA for dev mode is a later idea, see below |
| Health check | **no** | a readiness check that calls MONETA spends the merchant's rate limit on every probe |
| Rich Dev UI, codestart, Mutiny API, tracing | **no** | nothing to show, and nobody has asked; a `-reactive` sibling only if someone does |
| `META-INF/quarkus-skill.md` | **later** | a few lines, once the API has settled |

## Decisions

- **Two modules**, `modules/moneta-api-quarkus` (runtime) and `modules/moneta-api-quarkus-deployment`, beside
  the starter, both published to GitHub Packages with the other jars. Quarkus resolves the deployment jar from
  the same repository, so it has to be published too.
- **Gradle, with the `io.quarkus.extension` plugin.** The guide calls it experimental and says it may lack
  what the Maven plugin has. What this extension needs from it is the descriptor and the deployment link, which
  it does. If step 1 finds a gap, writing `META-INF/quarkus-extension.properties` by hand is the fallback;
  a Maven sub-build in a Gradle repo is not.
- **Compiled against the oldest supported Quarkus, 3.33 LTS**, `compileOnly` the BOM, the way the starter is
  compiled against Spring Boot 3.5. Supported: the two LTS lines still maintained, **3.33** (until
  25 Mar 2027) and **3.40** (until 30 Sep 2027). Quarkus 4.0 is planned for 25 Nov 2026; it gets a testing
  project once it is out, not before.
- **Config is `quarkus.moneta.*`**, an `@ConfigMapping` in phase `RUN_TIME`, since a token or a host is known
  only when the application starts. Quarkus's guide asks extensions to put their options under
  `quarkus.<extension>`, so the prefix differs from the starter's `moneta.*`; the names under it stay the same:
  `token`, `base-uri`, `request-timeout`, `partner.identification-number`, `.business-name`,
  `.application-name`.
- **Beans from a producer class in the runtime module**, registered with `AdditionalBeanBuildItem`:
  `@Singleton @DefaultBean MonetaClient`, closed by a `@Disposes` method on shutdown, and an `AccessToken` only
  when `quarkus.moneta.token` is set (`@LookupIfProperty`). A producer keeps injection of the config mapping,
  which a `SyntheticBeanBuildItem` gives up, and `@DefaultBean` lets the application's own `MonetaClient`
  replace it, as `@ConditionalOnMissingBean` does in Spring. Only what the application set reaches the
  builder, so each default stays in the client.
- **A bad token or partner value stops the application at startup**, as in Spring: the beans are created
  eagerly (`@Startup`, or an observer of `StartupEvent`), not on first use.
- **Native image: the deployment module registers the client's JSON records for reflection**
  (`ReflectiveClassBuildItem`), `MonetaJson`'s nested records among them, with their constructors and
  accessors. Records do need it: Jackson finds their canonical constructor by reflection, and the native
  image keeps nothing it was not told about. What else Jackson 3 needs inside a native image is the open
  question of step 2.
- **Jackson needs nothing from the caller.** Quarkus is on Jackson 2, and the one jar the two share,
  `jackson-annotations`, is pinned to 2.21 by both supported BOMs (read from `quarkus-bom` 3.33.3 and 3.40.1),
  which is what Jackson 3.2 needs. Neither BOM manages `tools.jackson`, so the client's own Jackson 3 resolves.
  That is the case Spring Boot 3.5.12 and older get wrong; the testing projects keep it checked.

## Steps

Each step is its own pull request.

1. **JVM mode.** The two modules, the config mapping, the producer, `FeatureBuildItem("moneta")`. Tests in the
   deployment module with `QuarkusUnitTest`: the bean appears; the token bean appears only with a token; an
   application bean replaces the client; a bad token fails the start; the client is closed on shutdown. A
   README for the extension with the property table, as the starter has.
2. **Native image.** The reflection registration, and one `@QuarkusIntegrationTest` built native that reads
   accounts and transactions from a fake MONETA (the JSON fixtures the client's tests already use, served by
   the JDK's `HttpServer`, no new dependency). It runs in CI on Mandrel in a container, as its own job: native
   builds take minutes, so not in `make build`. Settles what Jackson 3 needs in a native image, and whether a
   `reflect-config.json` in the client jar would serve callers outside Quarkus too.
3. **Testing projects.** `testing/quarkus/3.33` and `testing/quarkus/3.40`, each a caller's build with its own
   BOM and a `@QuarkusTest`, wired into `make build` beside `testing/spring-boot`. A Kotlin caller only if the
   Kotlin project's checks do not already cover what Quarkus adds.
4. **Docs.** `.docs/install.md` and the README name the extension; `.docs/compatibility.md` gets the Quarkus
   versions.

## Later, when someone needs it

- **Quarkiverse.** Listing in the Quarkus registry (`quarkus ext add`, code.quarkus.io) means a Quarkiverse
  repository under `io.quarkiverse.moneta`, Maven Central, an extension proposal issue in `quarkusio/quarkus`
  and the Quarkiverse build, which is Maven. It moves the code out of this repository, so it waits for users
  who ask.
- **A fake MONETA in dev mode**, as a Dev Service: dev mode then needs no token. Step 2's fake server is most
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
