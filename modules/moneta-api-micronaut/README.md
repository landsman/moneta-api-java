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
