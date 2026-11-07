# Play Framework integration

**A `moneta-api-play` jar that a Play application adds to its dependencies and configures under `moneta.*`,
nothing else.** It is what the Spring Boot starter is to Spring: one `MonetaClient` for the application, closed
on shutdown, and an `AccessToken` when the application reads only one account. The client itself does not change.

## What an ideal Play integration looks like

Play's own guide for library authors, [Writing Play Modules](https://playframework.com/documentation/latest/JavaPlayModules),
and the libraries that follow it (play-mailer, play-ebean) agree on four things:

| | How | Why |
|---|---|---|
| Wiring | a class extending `play.inject.Module`, returning bindings from `bindings(Environment, Config)` | not tied to Guice; any runtime DI Play supports loads it |
| Enabling | `play.modules.enabled += "…"` in the library's `reference.conf` | adding the jar is the whole setup; the application can still turn it off with `play.modules.disabled` |
| Configuration | HOCON keys read from the `Config` Play hands the module, durations as `30s` | the same file and syntax as the rest of the application, `${?ENV}` included |
| Shutdown | a stop hook on `ApplicationLifecycle` | a dev-mode reload stops the application, and a client left open leaks its threads on every reload |

What a library must not do there: disable another module in its `reference.conf` (the guide warns against it),
or read the environment itself. `token = ${?MONETA_TOKEN}` belongs in the application's `application.conf`.

## What the application writes

```hocon
# application.conf
moneta {
  token = ${?MONETA_TOKEN}       # only for an application reading one account
  base-uri = "https://…"         # the client's default, production, unless set
  request-timeout = 20s
  partner {
    identification-number = "12345678"
    business-name = "Kavarna"
    application-name = "cafe"
  }
}
```

```java
public class PaymentsController extends Controller {
    private final MonetaClient moneta;
    private final AccessToken token;
    private final BlockingExecutionContext blocking; // the application's own, see below
    private final ClassLoaderExecutionContext http;

    @Inject
    public PaymentsController(MonetaClient moneta, AccessToken token, BlockingExecutionContext blocking,
            ClassLoaderExecutionContext http) { … }

    public CompletionStage<Result> incoming() {
        return CompletableFuture.supplyAsync(() -> moneta.incoming(token, accountId, from, to), blocking)
                .thenApplyAsync(ts -> ok(views.html.incoming.render(ts)), http.current()); // render off the blocking pool
    }
}
```

The keys are the Spring starter's, in kebab case, so the two read the same in docs.

## Design

- **A new module `modules/moneta-api-play`**, published as `cz.insuit:moneta-api-play` beside the other two,
  with `api(project(":modules:moneta-api"))`.
- **Play is `compileOnly`**, as Spring Boot is in the starter: the application's Play is the one that runs. That
  also keeps the jar from dragging `play_2.13` into an application built for Scala 3 (`play_3`): the Java API in
  `play.inject` is the same class in both, and only a test can say the bytecode agrees, so one does (below).
- **`MonetaModule extends play.inject.Module`** binds `MonetaClient` to a provider that builds it from `moneta.*`
  and registers `close()` as a stop hook, **as a singleton** (`.in(Singleton.class)`): without it every injection
  builds another client with another stop hook, the leak the hook is there to prevent. It binds `AccessToken` only when `config.hasPath("moneta.token")`. A
  blank token fails the start, as in the starter.
- **`reference.conf` holds the `play.modules.enabled` line and nothing else.** Defaults stay in one place, the
  client's builder, as the starter already does.

## Blocking calls

**The client blocks, and Play's default thread pool must not.** Play runs controllers on a small pool sized to
the CPU; a MONETA read taking seconds there stalls every other request. Play's answer is a
`CustomExecutionContext` over a dispatcher of its own ([thread pools](https://playframework.com/documentation/latest/ThreadPools)).

The integration does not ship one. `CustomExecutionContext` takes a Pekko `ActorSystem` in Play 3.0 and an Akka
one in 2.9, so shipping it would split the jar in two. Instead `.docs/play.md` gives the five lines that define
it and the dispatcher block for `application.conf`; on Java 21 a virtual-thread executor is the shorter answer
and the doc says so, with its own stop hook, or a dev-mode reload leaks it as it would the client. Ship it in the jar if a second application writes the same class.

## Which Play

| | Status | Plan |
|---|---|---|
| Play 3.1 | milestones only, 3.1.0-M9; brings Jackson 2.21.2 | a `testing/play/3.1` once it is final |
| Play 3.0 (Pekko, `org.playframework`) | current, 3.0.12 from 2026-09-30 | compiled against, tested on Scala 2.13 and 3 |
| Play 2.9 (Akka, `com.typesafe.play`) | maintained beside 3.0, same releases (2.9.12) | tested; supported if the same jar passes, dropped from the plan if not |
| Play 2.8 and older | end of life, no Java 21 | no |

The client needs Java 21, which Play 3.0 and 2.9 run on.

## The risk: Jackson

**Play 3.0 and 2.9 pin Jackson 2.14.3** ([discussion](https://github.com/orgs/playframework/discussions/12469)), and the
client's Jackson 3.2 needs `jackson-annotations` 2.21, the jar both majors share; see
[compatibility](../compatibility.md). sbt takes the newest version on a conflict, so a Play application ends up
with databind 2.14 over annotations 2.21. Jackson promises annotations stay backward compatible, and 2.18 is
known to work, but 2.14 is not checked. The test therefore also calls Play's own `Json.toJson` and
`Json.fromJson` on a record, which is where a broken Play Jackson would show. If it fails, the fix goes into
`.docs/compatibility.md` as it did for Spring Boot 3.5.12. Play 3.1 moves to Jackson 2.21.2, so there the risk is
gone and the test only confirms it.

## Testing

`testing/play/3.0-scala-2.13`, `testing/play/3.0-scala-3` and `testing/play/2.9`, each only a
`build.gradle.kts` naming its Play, the test in `testing/play/shared`, as `testing/spring-boot` does. Gradle
resolves Play from Maven Central like any library; no sbt in this repo.

The test starts an application with `GuiceApplicationBuilder` and only the jar on the classpath, so the module
is found through `reference.conf` and not through the test's own bindings, then asserts:

1. `MonetaClient` is injected and reads transactions from a local server, as the Spring test does, and two
   injections get the same instance.
2. `AccessToken` is injected with `moneta.token` set, and missing without it.
3. Stopping the application closes the client.
4. Play's `Json` still round-trips a record (the Jackson risk above).

## Steps

1. Module skeleton, `MonetaModule`, `reference.conf`, unit test of the config mapping.
2. `testing/play/*` on Play 3.0, Scala 2.13 and 3, each in `settings.gradle.kts`; `org.playframework:*` and
   `com.typesafe.play:*` ignored in `.github/dependabot.yml`, as `spring-boot-dependencies` is, or a bump stops
   testing the version a folder names.
3. Play 2.9: keep or drop by its result.
4. `.docs/play.md` with the execution context, a row in `.docs/install.md`, a line in the README.
5. Publishing: the root build already publishes every `java-library` under `modules/`, so the release workflow
   needs nothing. The module gets the `runtimeClasspath` lock and its check, as the other two, and `make
   publish` / `publish-local` say three jars instead of both.

## Left out

- **The starter's HTTP hooks** (customizer, decorator, Micrometer, mTLS from an SSL bundle): Play has no bundles,
  and binding hooks through Guice multibinders would tie the module to Guice. An application that needs a proxy,
  mTLS or metrics disables the module with `play.modules.disabled` and binds its own client, built with
  `configureHttpClient` and `decorateHttpClient`; `.docs/play.md` shows it. Add the hooks when one asks.
- **Compile-time DI** (a `MonetaComponents` interface for an application without Guice): add when one asks.
- **A client on Play's `WSClient`**, async: a second transport for one framework. `Builder.httpClient` already
  takes the application's `HttpClient`; an async API belongs in the client, for every caller.
- **A Play example app**: it needs sbt beside Gradle. The testing project is the runnable proof; the café stays
  the one example.
