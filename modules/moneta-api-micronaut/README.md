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

A `MonetaClient` bean of the application's own replaces the one built here. A token the client would refuse stops
the application at startup, not at the first call, and the client is closed with the context.

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
`http.client.requests`, tagged by `MonetaClient::uriTemplate`, as under the starter; a bean that `@Replaces` the
one named `monetaHttpClientObservation` changes it.

Not in a GraalVM native image yet: the client binds MONETA's JSON by reflection and ships no reachability
metadata ([#19](https://github.com/landsman/moneta-api-java/issues/19)).

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
