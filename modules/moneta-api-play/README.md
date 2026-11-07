# moneta-api-play

A Play Framework module for the client: add the dependency, set `moneta.*`, inject `MonetaClient`. The jar's
`reference.conf` enables it; nothing else to register. Works on Play 3.0 (Scala 2.13 and 3) and 2.9;
[testing/play](../../testing/README.md) checks each.

| Key | Default | |
|-----|---------|---|
| `moneta.token` | — | one account's token; set it and an `AccessToken` can be injected. Leave it unset when the app serves many merchants and passes each one's token with the call. From the environment only. |
| `moneta.base-uri` | production, `MonetaClient.PRODUCTION` | the sandbox's host once the portal gives it |
| `moneta.request-timeout` | the client's, 30 s | one call to MONETA, as a HOCON duration: `20s` |
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | — | the registered application's headers, all three or none, ASCII only |

```hocon
# application.conf
moneta.token = ${?MONETA_TOKEN}
```

The keys are the Spring Boot starter's. The client is a singleton built when the application starts, so a token
or partner value it would refuse stops the start, and it is closed when the application stops, a dev-mode reload
included.

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
    private final MonetaClient moneta;
    private final AccessToken token;
    private final BlockingExecutor blocking;
    private final ClassLoaderExecutionContext http;

    @Inject
    public PaymentsController(
            MonetaClient moneta, AccessToken token, BlockingExecutor blocking, ClassLoaderExecutionContext http) { … }

    public CompletionStage<Result> incoming() {
        return CompletableFuture.supplyAsync(() -> moneta.incoming(token, accountId, from, to), blocking)
                .thenApplyAsync(ts -> ok(views.html.incoming.render(ts)), http.current()); // back off the blocking pool
    }
}
```

A dispatcher of Play's own does the same: a `CustomExecutionContext` subclass naming a `thread-pool-executor`
block in `application.conf`, as in Play's [thread pools](https://playframework.com/documentation/latest/ThreadPools)
guide. The module ships neither, because that class takes a Pekko `ActorSystem` in Play 3.0 and an Akka one in 2.9.

## A proxy, mTLS or metrics

The module builds a plain client. For anything the Spring starter configures with beans, disable it and bind your
own, closed the same way. `MonetaConfig.builder(config)` still reads `moneta.*`, so only the HTTP part is yours:

```hocon
play.modules.disabled += "cz.insuit.moneta.play.MonetaModule"
```

```java
// play.api.inject.ApplicationLifecycle, as the module uses: every application has it. play.inject's is bound
// only with play-java, so a Scala application without it would not start.
import play.api.inject.ApplicationLifecycle;

public class MonetaProvider implements Provider<MonetaClient> {
    @Inject Config config;
    @Inject ApplicationLifecycle lifecycle;

    @Override
    public MonetaClient get() {
        var client = MonetaConfig.builder(config)
                .configureHttpClient(http -> http.sslContext(mtls()))
                .decorateHttpClient(http -> MicrometerHttpClient.instrumentationBuilder(http, registry)
                        .uriMapper(MonetaClient::uriTemplate)
                        .build())
                .build();
        lifecycle.addStopHook(() -> {
            client.close();
            return CompletableFuture.completedFuture(null);
        });
        return client;
    }
}
```

bound with `bind(MonetaClient.class).toProvider(MonetaProvider.class).asEagerSingleton()` in a Guice module of
the application's.

## Jackson

Play 3.0 and 2.9 pin Jackson 2.14.3, and the client needs `jackson-annotations` 2.21 or newer; sbt and Gradle
both pick the client's, whichever it brings (2.22 as of this writing). Play's own `Json` runs on it; testing/play checks that too. Nothing to change in the build.
