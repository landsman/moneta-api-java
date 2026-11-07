# moneta-api-micronaut

Micronaut wiring for the client: add the dependency, set `moneta.*`, inject `MonetaClient`. One jar for
Micronaut 4.10 and 5; [testing/micronaut](../../testing/README.md) checks each.

```yaml
moneta:
  token: ${MONETA_TOKEN}
```

That is the whole setup for an application reading one account: inject `MonetaClient` and `AccessToken`. One
serving many merchants leaves `moneta.token` unset and passes each one's token with the call. Keep the token in
the environment, never in a committed file.

A `MonetaClient` bean of the application's own replaces the one built here. Like every Micronaut bean, the client
is built when first injected, not at startup, and closed with the context. A token or partner value it would refuse
fails when `moneta.*` is bound; to have that stop the start, without building the client, opt in as Micronaut
applications do:

```java
Micronaut.build(args).eagerInitConfiguration(true).mainClass(Application.class).start();
```

## A proxy, mTLS or metrics

Define an `HttpClientCustomizer` bean to change the HTTP client before it is built, an `HttpClientDecorator` bean
to wrap it; both apply in their `@Order`, and the client still owns and closes its HTTP client. There is no
`moneta.ssl.bundle` as in the starter, since it names a Spring Boot SSL bundle; mTLS is a customizer:

```java
@Singleton
HttpClientCustomizer monetaMtls(SSLContext moneta) {
    return http -> http.sslContext(moneta);
}
```

With `micrometer-java11` on the classpath and a `MeterRegistry` bean, every call is timed as
`http.client.requests`, tagged by `MonetaClient::uriTemplate`, as under the starter. A decorator replacing it
changes that, and one returning its argument turns it off:

```java
@Singleton
@Replaces(bean = HttpClientDecorator.class, named = "monetaHttpClientObservation")
HttpClientDecorator noMonetaObservation() {
    return http -> http;
}
```

## Logging and failures

The client writes one DEBUG line per call through `System.Logger`, as `cz.insuit.moneta.MonetaClient`, never
with the token. Unlike Spring Boot and Play, Micronaut with Logback does not route that into Logback by itself:
`System.Logger` falls back to `java.util.logging`, which Micronaut leaves alone. Add the SLF4J provider to the
application, and the usual level setting then applies:

```kotlin
runtimeOnly("org.slf4j:slf4j-jdk-platform-logging") // the SLF4J 2 version the application already has
```

```yaml
logger:
  levels:
    cz.insuit.moneta: DEBUG
```

An application that already bridges `java.util.logging` (Micronaut Starter's `jul-to-slf4j` feature, with
Logback's `LevelChangePropagator`) needs nothing more. The module adds neither, since that choice is the
application's.

Failures reach you as the client throws them, a `MonetaException`; the module catches nothing, retries nothing
and logs nothing above DEBUG. Map them where the application decides what its callers see, an
`ExceptionHandler` bean or an `@Error` route. MONETA refusing the token is not a 401 of your API:

```java
@Singleton
class MonetaFailures implements ExceptionHandler<MonetaException, HttpResponse<?>> {
    @Override
    public HttpResponse<?> handle(HttpRequest request, MonetaException failure) {
        return switch (failure) {
            case MonetaException.RateLimited limited -> HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE);
            case MonetaException.NoAnswer timeout -> HttpResponse.status(HttpStatus.GATEWAY_TIMEOUT);
            default -> HttpResponse.status(HttpStatus.BAD_GATEWAY);
        };
    }
}
```

In a GraalVM native image the client's own reachability metadata covers its JSON
([compatibility](../../.docs/compatibility.md#graalvm-native-image)); this module is not built natively in CI yet.

---

## Optional properties

None is needed against production. The names are the Spring Boot starter's.

| Property | Default | |
|----------|---------|---|
| `moneta.base-uri` | production, `MonetaClient.PRODUCTION` | the sandbox's host once the portal gives it |
| `moneta.request-timeout` | the client's, 30 s | one call to MONETA: `20s` |
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | — | the registered application's headers, all three or none, ASCII only |

MONETA does not ask for the partner headers: every call so far went through without ([how it
behaves](../../.docs/moneta-api.md)). Likely for an application approved on the developer portal; set them once the
portal says so.
