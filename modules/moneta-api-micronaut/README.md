# moneta-api-micronaut

Micronaut wiring for the client: add the dependency, set `moneta.*`, inject `MonetaClient`. One jar for
Micronaut 4.10 and 5; [testing/micronaut](../../testing/README.md) checks each.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("cz.insuit:moneta-api-micronaut:0.2.0")
}
```

<details><summary>Maven</summary>

```xml
<!-- in <dependencies> -->
<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>moneta-api-micronaut</artifactId>
    <version>0.2.0</version>
</dependency>
```

</details>

GitHub Packages asks for a token even for a public package: the repository block and the token are in
[Installing](../../.docs/install.md).

## Configure

```yaml
moneta:
  token: ${MONETA_TOKEN}
```

That is the whole setup for an application reading one account: inject `MonetaClient` and `AccessToken`. One
serving many account holders leaves `moneta.token` unset and passes each one's token with the call. Keep the token in
the environment, never in a committed file.

```java
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.AccountHolder;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Transaction;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Singleton
public class Payments {
    private final AccountHolder me;

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

<details><summary>Kotlin</summary>

```kotlin
import cz.insuit.moneta.AccessToken
import cz.insuit.moneta.MonetaClient
import cz.insuit.moneta.Transaction
import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.ZoneId

@Singleton
class Payments(moneta: MonetaClient, token: AccessToken) {
    private val me = moneta.with(token)

    /** Money received over the last week, booked. */
    fun receivedThisWeek(): List<Transaction> {
        val today = LocalDate.now(ZoneId.of("Europe/Prague"))
        return me.transactions().between(today.minusDays(7), today).incoming().booked().list()
    }
}
```

</details>

It reads the only account the token opens and throws `IllegalStateException` when there are more; then, or with
one token per account holder, see [More than one account](../../.docs/usage.md#more-than-one-account).
Without `moneta.token` there is no `AccessToken` bean to inject.

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
| `moneta.partner.identification-number`, `.business-name`, `.application-name` | none | the registered application's headers, all three or none, ASCII only |

MONETA does not ask for the partner headers: every call so far went through without ([how it
behaves](../../.docs/moneta-api.md)). Likely for an application approved on the developer portal; set them once the
portal says so.
