# moneta-api-quarkus

A Quarkus extension for the client: add the dependency, set `quarkus.moneta.*`, inject `MonetaClient`, in JVM
mode and as a native image. Compiled against Quarkus 3.33 LTS; [testing/quarkus](../../testing/README.md) checks
3.33 and 3.40.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("cz.insuit:moneta-api-quarkus:0.2.0")
}
```

<details><summary>Maven</summary>

```xml
<!-- in <dependencies> -->
<dependency>
    <groupId>cz.insuit</groupId>
    <artifactId>moneta-api-quarkus</artifactId>
    <version>0.2.0</version>
</dependency>
```

</details>

GitHub Packages asks for a token even for a public package: the repository block and the token are in
[Installing](../../.docs/install.md). Maven needs both, the `<repositories>` entry and the `<server>`, because
Quarkus fetches `moneta-api-quarkus-deployment` from there at build time.

## Configure

```properties
quarkus.moneta.token=${MONETA_TOKEN}
```

That is the whole setup for an application reading one account: inject `MonetaClient` and `AccessToken`. One
serving many account holders leaves `quarkus.moneta.token` unset and passes each one's token with the call; injecting
`AccessToken` then fails, naming the property. Keep the token in the environment, never in a committed file.

```java
import cz.insuit.moneta.AccessToken;
import cz.insuit.moneta.AccountHolder;
import cz.insuit.moneta.MonetaClient;
import cz.insuit.moneta.Transaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
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
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.time.ZoneId

@ApplicationScoped
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

| Property | Default |
|----------|---------|
| `quarkus.moneta.token` | none: no `AccessToken` bean |
| `quarkus.moneta.base-uri` | the client's, production |
| `quarkus.moneta.request-timeout` | the client's |
| `quarkus.moneta.partner.identification-number`, `.business-name`, `.application-name` | none: no partner headers; all three or none |
| `quarkus.moneta.tls-configuration-name` | none: the JDK's TLS |
| `quarkus.moneta.metrics.enabled` | `true`; fixed at build time |

The names are the Spring Boot starter's, under `quarkus.` as Quarkus asks of extensions. A `MonetaClient` bean of
the application's own replaces the one built here. The client is built at startup, so a token, partner value or
TLS name it refuses stops the application, and it is closed on shutdown.

## mTLS, a proxy or another hook

`quarkus.moneta.tls-configuration-name` names a `quarkus.tls.<name>.*` configuration from Quarkus's TLS
registry: a client certificate, a trust store, the protocols and ciphers.

```properties
quarkus.moneta.tls-configuration-name=moneta
quarkus.tls.moneta.key-store.p12.path=moneta.p12
quarkus.tls.moneta.key-store.p12.password=${MONETA_P12_PASSWORD}
```

Anything else is an `HttpClientCustomizer` bean, which changes the HTTP client before it is built, or an
`HttpClientDecorator` bean, which wraps it; both apply in `@Priority` order, highest first, after the TLS
configuration. The client still owns and closes its HTTP client.

## Metrics

With `quarkus-micrometer` and `micrometer-java11` in the application, every call is timed as
`http.client.requests`, tagged by `MonetaClient::uriTemplate`, so the `uri` tag holds
`/api/v4/vip/aisp/my/accounts/{accountId}/balance` and never an account id, as under the starter. Quarkus has
no binder for the JDK client of its own, so `micrometer-java11` is the one to add; the Quarkus BOM manages its
version.

```kotlin
implementation("io.quarkus:quarkus-micrometer-registry-prometheus") // or any registry
implementation("io.micrometer:micrometer-java11")
```

`quarkus.moneta.metrics.enabled=false` turns it off, for an application that wraps the client with an
`HttpClientDecorator` of its own instead. It is off under the OpenTelemetry Java agent too, which instruments the
JDK client already. Traces come from Quarkus's own OpenTelemetry, not from here.

## Native image

Works, with one line in the application's build: Quarkus's BOM pins `jackson-annotations` 2.21, and the client's
Jackson 3.2 links against 2.22, which a native build checks when it builds the image. The JVM needs nothing.
[Compatibility](../../.docs/compatibility.md#quarkus) has the fix for Gradle and Maven.
