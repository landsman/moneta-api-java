# Kotlin and Spring Boot

## Kotlin

The JSpecify annotations make `iban` a `String?` and a `when` over `MonetaException` exhaustive without an
`else`, enforced from Kotlin 2.1. Kotlin 1.9, which Spring Boot 3 manages, only warns; add
`-Xjspecify-annotations=strict` to the compiler options to make it an error.

## Spring Boot 3

The library uses Jackson 3, which lives beside a Spring Boot 3 application's Jackson 2 without conflict, except
for one jar the two share: `com.fasterxml.jackson.core:jackson-annotations`. Jackson 3.2 needs 2.21 or newer,
and Spring Boot's BOM pins it lower up to 3.5.12, so the client fails to load with `NoClassDefFoundError`. On
those versions raise it in your own dependency management; Jackson 2.18 databind runs on it.

```xml
<!-- Maven, in <dependencyManagement><dependencies> -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
    <version>2.21</version>
</dependency>
```

```kotlin
// Gradle with io.spring.dependency-management
dependencyManagement { dependencies { dependency("com.fasterxml.jackson.core:jackson-annotations:2.21") } }
```

Spring Boot 3.5.13 and newer, and every Spring Boot 4, need nothing. `make build` checks all three cases, and a
Kotlin caller, in [testing/](../testing/README.md).

## Your own HTTP stack

The client sends through a JDK `java.net.http.HttpClient` it builds itself: no redirects followed, no cookies
(one client serves many merchants' tokens), a 10-second connect timeout. Two hooks change it and keep that,
including that `close()` closes it:

```java
MonetaClient.builder()
        // before it is built: a proxy, an SSLContext for mTLS or a trust store, an executor
        .configureHttpClient(http -> http.proxy(ProxySelector.of(new InetSocketAddress("proxy.internal", 3128))))
        // after: a wrapper that delegates to it, for tracing or metrics; the first added is innermost
        .decorateHttpClient(http -> MicrometerHttpClient.instrumentationBuilder(http, meterRegistry)
                .observationRegistry(observationRegistry)
                .uriMapper(MonetaClient::uriTemplate)
                .build())
        .build();
```

With the starter, the same as beans, applied in their `@Order`:

```java
@Bean
MonetaHttpClientCustomizer monetaProxy() {
    return http -> http.proxy(ProxySelector.of(new InetSocketAddress("proxy.internal", 3128)));
}

@Bean
MonetaHttpClientDecorator monetaObservation(MeterRegistry meters, ObservationRegistry observations) {
    return http -> MicrometerHttpClient.instrumentationBuilder(http, meters)
            .observationRegistry(observations)
            .uriMapper(MonetaClient::uriTemplate)
            .build();
}
```

What to know before relying on it:

- **`configureHttpClient` can undo the library's settings too.** A cookie handler keeps one merchant's cookies
  for the next merchant's token, and following redirects sends a request somewhere MONETA did not answer from.
  Change what you came for, a proxy or TLS, and leave those alone.
- **A decorator wraps, it does not replace.** Returning another client skips the settings above and leaves the
  one the library built unused; whatever a decorator allocates itself is yours to close.
- **Both are ignored when you define your own `MonetaClient` bean**, since the starter then builds none.
- **Micrometer** ([`micrometer-java11`](https://docs.micrometer.io/micrometer/reference/reference/java-httpclient.html))
  tags every call `uri=UNKNOWN` unless given a `uriMapper`. Pass `.uriMapper(MonetaClient::uriTemplate)`: it names
  the endpoint without the account id, `/api/v4/vip/aisp/my/accounts/{accountId}/transactions`, so the tag stays
  low-cardinality. A mapper on the raw path would put account ids into it.
- **OpenTelemetry**: the Java agent instruments the JDK client with nothing configured, so add no decorator,
  Micrometer's or OpenTelemetry's, on top of it: each adds a second span per call. Without the agent, its
  [library instrumentation](https://opentelemetry.io/docs/languages/java/libraries/) wraps a JDK client; the
  method has changed name between releases, so take it from the version you use.
- **The JDK client cannot do everything** an OkHttp or Apache stack can: a SOCKS proxy is skipped silently, Basic
  auth on an HTTPS proxy tunnel is off by default (`jdk.http.auth.tunneling.disabledSchemes`), NTLM is missing,
  and interceptors written for those stacks do not apply. There is no transport hook for them yet.
- **A failure inside a wrapper**: an `UncheckedIOException` becomes `MonetaException.NoAnswer`, anything else
  reaches you as it was thrown.
- **`httpClient(…)`** still takes a client of your own, which you close, and which brings its own redirects,
  cookies and timeouts; `configureHttpClient` is refused beside it.

## Micronaut

No module. A factory is all it takes:

```java
@Factory
class MonetaFactory {
    @Singleton
    MonetaClient monetaClient() {
        return new MonetaClient();
    }
}
```

Whether a module with typed `moneta.*` is worth it, and what a GraalVM native image would need first, is
[#19](https://github.com/landsman/moneta-api-java/issues/19).
