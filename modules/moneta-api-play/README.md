# moneta-api-play

A Play Framework module for the client: add the dependency, set `moneta.*`, inject `MonetaClient`. The jar's
`reference.conf` enables it; nothing else to register. Works on Play 3.0 (Scala 2.13 and 3) and 2.9;
[testing/play](../../testing/README.md) checks each.

## Install

```scala
// build.sbt
resolvers += "GitHub Packages" at "https://maven.pkg.github.com/landsman/moneta-api-java"
credentials += Credentials("GitHub Package Registry", "maven.pkg.github.com", "gh", sys.env.getOrElse("GITHUB_TOKEN", ""))
libraryDependencies += "cz.insuit" % "moneta-api-play" % "0.2.0"
```

`GITHUB_TOKEN` holds a token with `read:packages`; Gradle and Maven below.

```kotlin
// build.gradle.kts
dependencies {
    implementation("cz.insuit:moneta-api-play:0.2.0")
}
```

<details><summary>Maven</summary>

```xml
<!-- in <dependencies> -->
<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>moneta-api-play</artifactId>
    <version>0.2.0</version>
</dependency>
```

</details>

GitHub Packages asks for a token even for a public package: the repository block and the token are in
[Installing](../../.docs/install.md).

## With a coding assistant

Paste this into it:

```text
Add the MONETA API client to this Play Framework project.

1. Read https://github.com/landsman/moneta-api-java/blob/main/llms.txt and https://github.com/landsman/moneta-api-java/blob/main/modules/moneta-api-play/README.md first. Do not guess the API.
2. Dependency: cz.insuit:moneta-api-play:0.2.0 from GitHub Packages, with sbt, Gradle or Maven, as
   https://github.com/landsman/moneta-api-java/blob/main/.docs/install.md shows. Never write a token into the project.
3. Configuration: moneta.token = ${?MONETA_TOKEN} in application.conf.
4. Code: inject MonetaClient and AccessToken (javax.inject), and run the calls on a pool of their own, not Play's default one. Bind the token with moneta.with(token), read with
   transactions().between(from, to), narrowed by incoming(), outgoing(), transfers(),
   cards(), booked() or pending(), then list().
5. Act on booked movements only, unless I ask for pending ones.
```

## Configure

```hocon
# application.conf
moneta.token = ${?MONETA_TOKEN}
```

That is the whole setup for an application reading one account: inject `MonetaClient` and `AccessToken`. One
serving many account holders leaves `moneta.token` unset and passes each one's token with the call. Keep the token in
the environment, never in a committed file.

```java
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.AccountHolder;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Transaction;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Payments {
    private final AccountHolder me;

    @Inject
    public Payments(MonetaClient moneta, AccessToken token) {
        this.me = moneta.with(token);
    }

    /** Money received over the last week, booked. */
    public List<Transaction> receivedThisWeek() {
        var today = LocalDate.now(ZoneId.of("Europe/Prague"));
        return me.transactions().between(today.minusDays(7), today).incoming().booked().list();
    }
}
```

It reads the only account the token opens and throws `IllegalStateException` when there are more; then, or with
one token per account holder, see [More than one account](../../.docs/usage.md#more-than-one-account).
Without `moneta.token` there is no `AccessToken` bean to inject.

The call blocks: run it on [a pool of its own](#calls-block-give-them-a-pool-of-their-own), not Play's default one.

The client is a singleton built when the application starts, so a token it would refuse stops the start, and it is
closed when the application stops, a dev-mode reload included.

## Calls block: give them a pool of their own

The client waits for MONETA on the calling thread. Play's default pool is sized to the CPU, and a call taking
seconds there stalls every other request. On Java 21 a virtual thread per call is the short answer; close it with
the application, or a dev-mode reload leaks it:

```java
@Singleton
public class BlockingExecutor implements Executor {
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    @Inject
    public BlockingExecutor(ApplicationLifecycle lifecycle) {
        lifecycle.addStopHook(() -> {
            threads.close();
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public void execute(Runnable task) {
        threads.execute(task);
    }
}
```

```java
public class PaymentsController extends Controller {
    private final Payments payments;
    private final BlockingExecutor blocking;
    private final ClassLoaderExecutionContext http;

    @Inject
    public PaymentsController(Payments payments, BlockingExecutor blocking, ClassLoaderExecutionContext http) { … }

    public CompletionStage<Result> received() {
        return CompletableFuture.supplyAsync(payments::receivedThisWeek, blocking)
                .thenApplyAsync(ts -> ok(views.html.received.render(ts)), http.current()); // back off the blocking pool
    }
}
```

A dispatcher of Play's own does the same: a `CustomExecutionContext` subclass naming a `thread-pool-executor` block
in `application.conf`, as in Play's [thread pools](https://playframework.com/documentation/latest/ThreadPools)
guide. The module ships neither, because that class takes a Pekko `ActorSystem` in Play 3.0 and an Akka one in 2.9.

## A proxy, mTLS or metrics

List `HttpClientCustomizer` and `HttpClientDecorator` classes under `moneta.http`, as `play.filters.enabled` lists
filters. Play's injector makes each, so it can take what it needs; they apply in the listed order, customizers
before decorators, and the client still owns and closes its HTTP client.

```hocon
moneta.http {
  customizers = ["app.moneta.Mtls"]
  decorators = ["app.moneta.Metrics"]
}
```

```java
public class Mtls implements HttpClientCustomizer {
    @Override
    public void customize(HttpClient.Builder http) {
        http.sslContext(sslContextFromYourKeyStore());
    }
}

public class Metrics implements HttpClientDecorator {
    private final MeterRegistry registry;

    @Inject
    public Metrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public HttpClient decorate(HttpClient http) {
        return MicrometerHttpClient.instrumentationBuilder(http, registry)
                .uriMapper(MonetaClient::uriTemplate)
                .build();
    }
}
```

A name that is not such a class stops the start. Unlike the Spring starter, nothing instruments by itself: Play has
no `MeterRegistry` of its own to find.

To build the client entirely yourself, disable the module with
`play.modules.disabled += "cz.insuit.moneta.play.MonetaModule"` and start from `MonetaConfig.builder(config)`, which still reads `moneta.*`;
close the client in a stop hook on `play.api.inject.ApplicationLifecycle`, which every application has, unlike
`play.inject`'s, bound only with play-java.

## Jackson

Play 3.0 and 2.9 pin Jackson 2.14.3, and the client needs `jackson-annotations` 2.21 or newer; sbt and Gradle both
pick the client's, whichever it brings (2.22 as of this writing). Play's own `Json` runs on it; testing/play checks
that too. Nothing to change in the build.

---

## Optional keys

None is needed against production. The keys are the Spring Boot starter's.

| Key | Default | |
|-----|---------|---|
| `moneta.base-uri` | production, `MonetaClient.PRODUCTION` | a test server; the sandbox is the same host, chosen by its token |
| `moneta.request-timeout` | the client's, 30 s | one call to MONETA, as a HOCON duration: `20s` |
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | none | the registered application's headers, all three or none, ASCII only |

MONETA does not ask for the partner headers: its specification marks them optional, and every live call went
through without ([how it behaves](../../.docs/moneta-api.md)).
